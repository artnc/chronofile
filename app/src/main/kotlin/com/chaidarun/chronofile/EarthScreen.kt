// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Zoom bounds: 0.8 lets the whole world shrink slightly inside the viewport; 10000 zooms to
// building level so entries at distinct nearby coordinates separate into their own clusters
private const val MAX_SCALE = 10000f
private const val MIN_SCALE = 0.8f

/**
 * Latitude the initial fit crops to at the bottom. We remove Antarctica from the basemap, so
 * fitting the full 90..-90 span would leave an empty ocean band below the southernmost land
 * (~-55.5° at Cape Horn). Fitting only 90..this latitude crops that band while leaving a small
 * ocean margin below the tip of South America
 */
private const val INITIAL_SOUTH_LAT = -60.0

/** Geometries whose centroids fall within this many dp of a tap select that cluster */
private val HIT_DP = 28.dp

/**
 * Grid cell size: entries closer than this many screen dp at the current zoom merge into a cluster
 */
private val CLUSTER_DP = 44.dp

/** Radius of a single-entry pin */
private val PIN_RADIUS_DP = 5.dp

/** Radius the largest cluster reaches; every smaller circle scales down from this */
private val MAX_RADIUS_DP = 40.dp

/** Minimum empty space kept between neighboring cluster circles */
private val GAP_DP = 2.dp

/** Activities that mark a run as airport transit, making it a layover candidate */
private val TRANSIT_ACTIVITIES =
  setOf(
    "air",
    "air travel",
    "airport",
    "airplane",
    "airplane flight",
    "flight",
    "fly",
    "flying",
    "layover",
    "plane",
    "plane flight",
  )

/** Max distance from a layover's first located entry for the rest to count as the same airport */
private const val LAYOVER_RADIUS_MI = 4.0

/**
 * A jump at least this far (miles) between neighboring located entries implies a flight, not ground
 * travel, between them. In the data, ground hops between logged points top out ~35mi while the
 * shortest flight leg is ~44mi, so 40 cleanly splits them
 */
private const val LAYOVER_JUMP_MI = 40.0

/** Great-circle distance in miles between two (lat, lon) points */
private fun haversineMi(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
  val dLat = Math.toRadians(b.first - a.first)
  val dLon = Math.toRadians(b.second - a.second)
  val s =
    sin(dLat / 2).pow(2) +
      cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLon / 2).pow(2)
  return 2 * 3958.8 * asin(sqrt(s))
}

/** Style for the count label drawn inside a multi-entry cluster circle */
private val countLabelStyle =
  TextStyle(color = ColorPrimaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)

/**
 * What number a cluster is sized and labeled by: [Activities] counts every located entry, [Days]
 * counts the distinct calendar days those entries fall on
 */
private enum class MapMode {
  Days,
  Activities,
}

// Minimal GeoJSON shapes: mapshaper emits a GeometryCollection (not FeatureCollection) once feature
// properties are stripped. Geometry entries are nullable since Natural Earth emits empty ones
@Serializable private class GeometryCollection(val geometries: List<Geometry?>)

@Serializable private class Geometry(val coordinates: JsonElement, val type: String)

// Lenient since we only read geometries and skip the unused top-level type/bbox keys
private val geoJson = Json { ignoreUnknownKeys = true }

/**
 * A drawn group of entries. [center] is a normalized world point (mean of its members), projected
 * with the live zoom so clusters track the map even while re-grouping is throttled during a pinch
 */
private class Cluster(val center: Offset, val entries: List<Entry>)

/**
 * Mutable accumulator used while clustering: tracks the running screen-pixel sum and its members
 */
private class MutCluster(val entries: MutableList<Entry>, var sumX: Float, var sumY: Float) {
  val count
    get() = entries.size

  val cx
    get() = sumX / count

  val cy
    get() = sumY / count

  fun absorb(other: MutCluster) {
    entries.addAll(other.entries)
    sumX += other.sumX
    sumY += other.sumY
  }

  fun add(x: Float, y: Float, entry: Entry) {
    entries.add(entry)
    sumX += x
    sumY += y
  }
}

/**
 * Space (in pixels) a cluster reserves while merging, by entry count: grows with count up to
 * [maxRadius] so big clusters stay bounded. The drawn radius is scaled separately (see the draw
 * pass) and clamped to this, so no two circles overlap regardless of the active mode
 */
private fun clusterRadius(count: Int, pinRadius: Float, maxRadius: Float) =
  if (count == 1) pinRadius else (pinRadius + ln(count.toFloat()) * 4f).coerceAtMost(maxRadius)

/** Projects a [lat, lon] pair to a normalized equirectangular point (x, y both in 0..1) */
private fun normalize(latLon: Pair<Double, Double>) =
  Offset(((latLon.second + 180) / 360).toFloat(), ((90 - latLon.first) / 180).toFloat())

/** Maps a normalized world point to screen pixels under the current pan/zoom */
private fun toScreen(n: Offset, baseW: Float, baseH: Float, scale: Float, offset: Offset) =
  Offset(n.x * baseW * scale + offset.x, n.y * baseH * scale + offset.y)

/** Flattens a GeoJSON ring (array of [lon, lat] positions) to normalized world points */
private fun ringToOffsets(ring: JsonElement) =
  ring.jsonArray.map {
    val pos = it.jsonArray
    normalize(Pair(pos[1].jsonPrimitive.double, pos[0].jsonPrimitive.double))
  }

/**
 * Parses the offline basemap into per-country outer rings in normalized world space. Keeps only
 * each polygon's outer ring (index 0), so interior holes like the Caspian are filled in
 */
private fun parseCountries(): List<List<Offset>> {
  val text = App.ctx.assets.open("countries.geojson").bufferedReader().use { it.readText() }
  val rings = mutableListOf<List<Offset>>()
  for (geometry in geoJson.decodeFromString<GeometryCollection>(text).geometries) {
    if (geometry == null) continue
    when (geometry.type) {
      "MultiPolygon" ->
        for (polygon in geometry.coordinates.jsonArray) {
          rings.add(ringToOffsets(polygon.jsonArray[0]))
        }
      "Polygon" -> rings.add(ringToOffsets(geometry.coordinates.jsonArray[0]))
    }
  }
  return rings
}

/**
 * Groups located entries for the current zoom in two stages. First a uniform grid (cell size in
 * offset-free screen pixels) buckets nearby points; identical coordinates (e.g. a home address with
 * thousands of entries) collapse for free. Then any two clusters whose circles would overlap are
 * agglomeratively merged, so unlike a plain grid no two drawn circles overlap, yet every entry
 * stays reachable inside some cluster (a big cluster is never split into colliding pieces)
 */
private fun clusterEntries(
  points: List<Pair<Offset, Entry>>,
  baseW: Float,
  baseH: Float,
  scale: Float,
  cellPx: Float,
  maxRadius: Float,
  pinRadius: Float,
  gap: Float,
): List<Cluster> {
  // Grid-bin in offset-free screen space (1 unit == 1 pixel)
  val cells = mutableMapOf<Long, MutCluster>()
  for ((point, entry) in points) {
    val sx = point.x * baseW * scale
    val sy = point.y * baseH * scale
    // Pack the 2D cell index into one Long key
    val key = (floor(sx / cellPx).toLong() shl 32) or (floor(sy / cellPx).toLong() and 0xffffffffL)
    cells.getOrPut(key) { MutCluster(mutableListOf(), 0f, 0f) }.add(sx, sy, entry)
  }

  // Merge any two clusters whose circles overlap, repeating until none do. A merge grows the
  // combined cluster's radius, which can create new overlaps, hence the restart-on-merge loop
  val clusters = cells.values.toMutableList()
  var merged = true
  while (merged) {
    merged = false
    outer@ for (i in clusters.indices) {
      val a = clusters[i]
      val ra = clusterRadius(a.count, pinRadius, maxRadius)
      for (j in i + 1 until clusters.size) {
        val b = clusters[j]
        val dx = a.cx - b.cx
        val dy = a.cy - b.cy
        // Keep at least `gap` empty pixels between neighboring circles, not just touching
        val minDist = ra + clusterRadius(b.count, pinRadius, maxRadius) + gap
        if (dx * dx + dy * dy < minDist * minDist) {
          a.absorb(b)
          clusters.removeAt(j)
          merged = true
          break@outer
        }
      }
    }
  }
  // Convert the pixel-space centers (computed at this scale) back to normalized world points so the
  // draw pass can project them with the live zoom
  return clusters.map {
    Cluster(Offset(it.cx / (baseW * scale), it.cy / (baseH * scale)), it.entries)
  }
}

/**
 * Centers the map when it's smaller than the viewport, else clamps so it can't be panned off-screen
 */
private fun clampOffset(
  offset: Offset,
  scale: Float,
  size: IntSize,
  baseW: Float,
  baseH: Float,
): Offset {
  val mapW = baseW * scale
  val mapH = baseH * scale
  val x =
    if (mapW <= size.width) (size.width - mapW) / 2f else offset.x.coerceIn(size.width - mapW, 0f)
  val y =
    if (mapH <= size.height) (size.height - mapH) / 2f
    else offset.y.coerceIn(size.height - mapH, 0f)
  return Offset(x, y)
}

@Composable
fun EarthScreen(viewModel: MainViewModel, onNavigateUp: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val history = state.history
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val clusterPx = with(density) { CLUSTER_DP.toPx() }
  val gapPx = with(density) { GAP_DP.toPx() }
  val hitPx = with(density) { HIT_DP.toPx() }
  val maxRadiusPx = with(density) { MAX_RADIUS_DP.toPx() }
  val pinRadiusPx = with(density) { PIN_RADIUS_DP.toPx() }

  // Parse the basemap once off the main thread; an empty list (missing/corrupt asset) still draws
  // the pins on a blank background rather than crashing. Null while still parsing so the fade-in
  // waits for the real result instead of flashing the empty initial value
  val countries by
    produceState<List<List<Offset>>?>(null) {
      value =
        withContext(Dispatchers.Default) {
          try {
            parseCountries()
          } catch (e: Exception) {
            Log.w(TAG, "Failed to parse basemap", e)
            emptyList()
          }
        }
    }

  // Normalized world points for located entries, recomputed only when history changes
  val points =
    remember(history) {
      history?.entries.orEmpty().mapNotNull { e -> e.latLon?.let { normalize(it) to e } }
    }

  // Start times of entries that belong to an airport layover. A layover is a tight (<=4mi) run of
  // transit entries we both flew into and out of, spotted by a long location jump on either side.
  // Going by jumps rather than logged flights catches connections even when a flight leg wasn't
  // logged, and folds in frozen-GPS in-flight entries that pile up at the departure airport.
  // Origins (drove in, flew out) and destinations (flew in, drove out) have a jump on only one
  // side, so they survive. Keyed by startTime (unique) so membership tests stay cheap; see
  // TRANSIT_ACTIVITIES for the set.
  val layovers =
    remember(points) {
      val located = points.map { it.second }
      val ll = located.map { it.latLon!! }
      buildSet {
        var s = 0
        while (s < located.size) {
          // Grow a run of entries within 4mi of its first entry (a single airport)
          var e = s
          while (e + 1 < located.size && haversineMi(ll[e + 1], ll[s]) <= LAYOVER_RADIUS_MI) e++
          // Hide it only if it is airport transit reached and left by air (a jump on both sides)
          val isTransit = (s..e).any { located[it].activity.lowercase() in TRANSIT_ACTIVITIES }
          val flewIn = s > 0 && haversineMi(ll[s - 1], ll[s]) >= LAYOVER_JUMP_MI
          val flewOut = e < located.size - 1 && haversineMi(ll[e + 1], ll[e]) >= LAYOVER_JUMP_MI
          if (isTransit && flewIn && flewOut) for (k in s..e) add(located[k].startTime)
          s = e + 1
        }
      }
    }

  var canvasSize by remember { mutableStateOf(IntSize.Zero) }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Unspecified) }
  var selected by remember { mutableStateOf<List<Entry>?>(null) }
  // Whether circles count every entry or only the distinct days they fall on
  var mode by remember { mutableStateOf(MapMode.Days) }
  // Whether layover (airport connection) entries are shown; off by default to declutter the map
  var includeLayovers by remember { mutableStateOf(false) }
  // Stop auto-fitting once the user pans/zooms so we don't yank the view back under their finger
  var userMoved by remember { mutableStateOf(false) }
  // Longitude to center on initially: the newest located timeline entry, or the prime meridian if
  // none. Entries are chronological, so the last one with coordinates is the most recent
  val initialLon =
    remember(history) {
      history?.entries.orEmpty().lastOrNull { it.latLon != null }?.latLon?.second ?: 0.0
    }

  val baseW = canvasSize.width.toFloat()
  val baseH = baseW / 2f

  // Fit latitudes 90..INITIAL_SOUTH_LAT to the viewport height (the Arctic flush with the top edge,
  // the cropped southern latitude with the bottom) and center horizontally on initialLon. Re-runs
  // on every size change so a transient first measurement self-corrects rather than locking in a
  // wrong (over-zoomed) scale, but stops once the user has moved the map
  LaunchedEffect(canvasSize, initialLon, userMoved) {
    if (userMoved || baseW == 0f) return@LaunchedEffect
    // baseH (= baseW/2) is the map's full 90..-90 height at scale 1. Dividing by the cropped band's
    // normalized height makes that band (not the whole map) exactly fill the viewport height
    val southY = normalize(Pair(INITIAL_SOUTH_LAT, 0.0)).y
    scale = (canvasSize.height / (baseH * southY)).coerceIn(MIN_SCALE, MAX_SCALE)
    val nx = normalize(Pair(0.0, initialLon)).x
    offset =
      clampOffset(Offset(baseW / 2f - nx * baseW * scale, 0f), scale, canvasSize, baseW, baseH)
  }

  // Throttle re-clustering: the merge pass is expensive, so regroup only ~150ms after the last zoom
  // change (i.e. once a pinch settles), not on every intermediate frame. collectLatest cancels the
  // pending delay whenever the zoom changes again, debouncing it. Skip the delay until the user
  // first pans/zooms so the initial fit clusters at the right scale immediately, instead of merging
  // into one blob at the default scale and visibly splitting apart ~150ms later
  var clusterScale by remember { mutableFloatStateOf(scale) }
  LaunchedEffect(Unit) {
    snapshotFlow { scale }
      .collectLatest {
        if (userMoved) delay(150)
        clusterScale = it
      }
  }

  // Drop layover entries unless the user opts to include them
  val visiblePoints =
    remember(points, layovers, includeLayovers) {
      if (includeLayovers) points else points.filterNot { (_, e) -> e.startTime in layovers }
    }

  // Re-cluster on the throttled zoom or a data change, never on pan
  val clusters =
    remember(visiblePoints, clusterScale, baseW) {
      if (baseW == 0f) emptyList()
      else
        clusterEntries(
          visiblePoints,
          baseW,
          baseH,
          clusterScale,
          clusterPx,
          maxRadiusPx,
          pinRadiusPx,
          gapPx,
        )
    }

  // Country paths in scale-1 pixel space (normalized × base dims), rebuilt only when the geometry
  // or canvas width changes. The draw pass then re-applies just a pan+zoom transform each frame
  // instead of allocating a fresh Path and re-projecting every vertex
  val countryPaths =
    remember(countries, baseW) {
      countries.orEmpty().map { ring ->
        Path().apply {
          ring.forEachIndexed { i, n ->
            if (i == 0) moveTo(n.x * baseW, n.y * baseH) else lineTo(n.x * baseW, n.y * baseH)
          }
          close()
        }
      }
    }

  // Per-cluster number to size and label by: entry count in Activities mode, distinct-day count in
  // Days mode. Recomputed only on a re-cluster or mode flip, not per frame
  val clusterValues =
    remember(clusters, mode) {
      clusters.map {
        if (mode == MapMode.Days) it.entries.distinctBy { e -> getDate(e.startTime) }.size
        else it.entries.size
      }
    }

  // Largest value across clusters in the active mode. The cluster holding it is drawn at the full
  // MAX_RADIUS and every other circle scales down from there
  val globalMax = remember(clusterValues) { clusterValues.maxOrNull() ?: 1 }

  // Drawn radius per cluster, on a log curve so the global-max cluster reaches MAX_RADIUS and
  // smaller ones shrink in proportion (value 1 stays a pin). Clamp to the space the clustering pass
  // reserved for this cluster (sized by entry count) so circles never overlap, even though grouping
  // is mode-independent while this value is per-mode. Precomputed (not per frame) since it changes
  // only on a re-cluster or mode flip
  val clusterRadii =
    remember(clusters, clusterValues, globalMax) {
      val lnMax = ln(globalMax.toFloat())
      clusters.mapIndexed { i, cluster ->
        if (globalMax <= 1) pinRadiusPx
        else
          minOf(
            pinRadiusPx + (maxRadiusPx - pinRadiusPx) * (ln(clusterValues[i].toFloat()) / lnMax),
            clusterRadius(cluster.entries.size, pinRadiusPx, maxRadiusPx),
          )
      }
    }

  // Pre-measure each distinct label once; drawing them every frame would otherwise re-run text
  // layout for every visible multi-entry circle
  val countLabels =
    remember(clusterValues) {
      clusterValues
        .filter { it > 1 }
        .distinct()
        .associateWith { textMeasurer.measure(it.toString(), style = countLabelStyle) }
    }

  // Fade the map in once the basemap has parsed and the view is fit, instead of flashing an empty
  // ocean for the ~0.5s the off-thread parse takes
  val contentAlpha by
    animateFloatAsState(
      if (countries != null && offset != Offset.Unspecified) 1f else 0f,
      label = "earthFade",
    )

  AppScaffold(title = "Map", onNavigateUp = onNavigateUp) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      Canvas(
        modifier =
          Modifier.fillMaxSize()
            .alpha(contentAlpha)
            .onSizeChanged { canvasSize = it }
            // scale/offset are MutableState so reads live; key on canvasSize to refresh baseW/baseH
            .pointerInput(canvasSize) {
              detectTransformGestures { centroid, pan, zoom, _ ->
                userMoved = true
                val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                // Anchor the gesture centroid through the zoom, then apply the pan
                val zoomed = centroid + (offset - centroid) * (newScale / scale) + pan
                scale = newScale
                offset = clampOffset(zoomed, newScale, canvasSize, baseW, baseH)
              }
            }
            // Key on clusters too so the hit-test sees the current grouping after a zoom
            .pointerInput(canvasSize, clusters) {
              detectTapGestures { tap ->
                // Pick the nearest cluster within the hit radius and list its entries, newest first
                val hit =
                  clusters
                    .map {
                      it to (toScreen(it.center, baseW, baseH, scale, offset) - tap).getDistance()
                    }
                    .filter { it.second <= hitPx }
                    .minByOrNull { it.second }
                    ?.first
                if (hit != null) {
                  selected = hit.entries.sortedByDescending { it.startTime }
                }
              }
            }
      ) {
        if (offset == Offset.Unspecified) return@Canvas

        // Draw countries: faint fill plus a thin lighter outline that renders the borders. The
        // cached paths live in scale-1 pixel space, so one affine transform (pan then uniform zoom)
        // places them without rebuilding any geometry per frame. Pre-divide the stroke width by
        // scale so the outline stays 1dp wide after the transform scales it back up
        // Opaque equivalent of a 22%-white line over the ocean. Opaque strokes don't accumulate, so
        // internal country borders (stroked twice, once per adjacent country) render the same as
        // the single-stroked coastlines instead of looking brighter
        val outlineColor = Color.White.copy(alpha = 0.22f).compositeOver(ColorPrimaryDark)
        val outlineStroke = Stroke(width = 1.dp.toPx() / scale)
        withTransform({
          translate(offset.x, offset.y)
          scale(scale, scale, Offset.Zero)
        }) {
          for (path in countryPaths) {
            drawPath(path, color = ColorPrimary)
            drawPath(path, color = outlineColor, style = outlineStroke)
          }
        }

        // Draw every cluster (clustering already guarantees no two circles overlap): a value of 1
        // (one entry, or one distinct day in Days mode) renders as a small unlabeled pin, more as a
        // larger value-labeled circle
        clusters.forEachIndexed { i, cluster ->
          val s = toScreen(cluster.center, baseW, baseH, scale, offset)
          // Cull clusters outside the viewport (plus a margin for partially-visible circles)
          if (s.x < -50 || s.y < -50 || s.x > size.width + 50 || s.y > size.height + 50)
            return@forEachIndexed
          val value = clusterValues[i]
          drawCircle(ColorAccent, radius = clusterRadii[i], center = s)
          if (value > 1) {
            val label = countLabels.getValue(value)
            drawText(
              label,
              topLeft = Offset(s.x - label.size.width / 2f, s.y - label.size.height / 2f),
            )
          }
        }
      }

      // Mode toggle, bottom-left. Its background is the ocean color, so the panel is invisible over
      // water yet contrasts against the brighter land it may overlap
      Column(
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ColorPrimaryDark)
            .padding(vertical = 8.dp, horizontal = 14.dp)
      ) {
        Column(Modifier.selectableGroup()) {
          for (m in MapMode.entries) {
            AppRadio(selected = mode == m, onClick = { mode = m }, label = m.name)
          }
        }
        AppCheckbox(
          checked = includeLayovers,
          onCheckedChange = { includeLayovers = it },
          label = "Include layovers",
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
      }
    }
  }

  selected?.let { entries ->
    // Reverse-geocode the newest entry's coordinates to label the cluster by location
    val title by rememberGeocodedTitle(entries.first())
    AlertDialog(
      onDismissRequest = { selected = null },
      title = { Text(title, style = MaterialTheme.typography.bodyLarge) },
      text = {
        // Scrollable list of the 100 newest entries at this location, reverse-chronological
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
          items(entries.take(100)) { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
              Text(
                "${formatDate(entry.startTime)}, ${formatTime(Date(entry.startTime * 1000))}",
                color = ColorFadedText,
                style = MaterialTheme.typography.bodyMedium,
              )
              Text(
                listOfNotNull(entry.activity, entry.note).joinToString(" · "),
                color = ColorSecondaryText,
              )
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
      containerColor = ColorPrimaryDark,
    )
  }
}
