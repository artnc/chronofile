// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import info.appdev.charting.components.Legend
import kotlin.math.sqrt

/** D3.js schemeTableau10 and schemeDark2 */
val CHART_COLORS by lazy {
  listOf(
      "#4e79a7",
      "#f28e2c",
      "#e15759",
      "#76b7b2",
      "#59a14f",
      "#edc949",
      "#af7aa1",
      "#ff9da7",
      "#9c755f",
      "#bab0ab",
      "#1b9e77",
      "#d95f02",
      "#7570b3",
      "#e7298a",
      "#66a61e",
      "#e6ab02",
      "#a6761d",
      "#666666",
    )
    .map { it.toColorInt() }
}

const val CHART_LABEL_COLOR = Color.WHITE
const val CHART_LABEL_FONT_SIZE = 12f
const val OTHER_SLICE_NAME = "Other"

/**
 * Edge padding for the custom Canvas-based chart tabs (matrix, count), so they don't run flush to
 * the viewport. The MPAndroidChart tabs supply their own insets via [ChartScaffold]'s chartPadding
 */
val CHART_PADDING = 16.dp

/** Slices smaller than this will get bucketed into "Other" */
private const val MIN_SLICE_PERCENT = 0.015

enum class Aggregation {
  DAY,
  DAY_OF_WEEK,
  TOTAL,
}

/**
 * Determines the date range that should be used to render each chart.
 *
 * This takes into account the earliest recorded entry, the last recorded entry, the user-selected
 * start date, the user-selected end date, and the chart metric.
 */
fun getChartRange(history: History, chartConfig: ChartConfig): Pair<Long, Long> {
  val historyEnd = history.currentActivityStartTime
  val historyStart = history.entries.getOrNull(0)?.startTime ?: historyEnd
  val pickerStart = chartConfig.startTime ?: 0
  val pickerEnd =
    if (chartConfig.endTime == null) {
      Long.MAX_VALUE
    } else {
      // Must append a day's worth of seconds to the range to make it inclusive
      chartConfig.endTime + DAY_SECONDS
    }
  val rangeEnd = minOf(historyEnd, pickerEnd)
  var rangeStart = maxOf(historyStart, pickerStart)
  if (chartConfig.metric == Metric.AVERAGE) {
    rangeStart = rangeEnd - (rangeEnd - rangeStart) / DAY_SECONDS * DAY_SECONDS
  }
  return Pair(rangeStart, rangeEnd)
}

/** Returns a ({ bucket: { slice: duration } }, [(slice, total duration)]) */
fun aggregateEntries(
  config: Config,
  history: History,
  chartConfig: ChartConfig,
  rangeStart: Long,
  rangeEnd: Long,
  aggregation: Aggregation,
): Pair<Map<Long, Map<String, Long>>, List<Pair<String, Long>>> {
  val grouped = chartConfig.grouped
  var endTime = rangeEnd
  val buckets = mutableMapOf<Long, MutableMap<String, Long>>()
  val sliceMap = mutableMapOf<String, Long>()
  for (entry in history.entries.reversed()) {
    // Skip entries from after date range
    if (entry.startTime >= rangeEnd) {
      continue
    }

    // Get slice(s) depending on whether entry crosses midnight
    val startTime = maxOf(entry.startTime, rangeStart)
    val bucketIncrements: Map<Long, Long> =
      when (aggregation) {
        Aggregation.DAY,
        Aggregation.DAY_OF_WEEK -> {
          val midnightBeforeStart = getPreviousMidnight(startTime)
          val midnightBeforeEnd = getPreviousMidnight(endTime)
          val pieces =
            if (midnightBeforeStart != midnightBeforeEnd) {
              mapOf(
                midnightBeforeEnd to (endTime - midnightBeforeEnd),
                midnightBeforeStart to (midnightBeforeEnd - startTime),
              )
            } else {
              mapOf(midnightBeforeStart to (endTime - startTime))
            }

          if (aggregation == Aggregation.DAY_OF_WEEK)
            pieces.mapKeys { getDayOfWeek(it.key).value.toLong() }
          else pieces
        }
        Aggregation.TOTAL -> mapOf(0L to (endTime - startTime))
      }

    // Record slices
    val slice = if (grouped) config.getActivityGroup(entry.activity) else entry.activity
    bucketIncrements.forEach { (bucket, increment) ->
      val bucketSlices = buckets.getOrPut(bucket) { mutableMapOf() }
      bucketSlices[slice] = bucketSlices.getOrDefault(slice, 0) + increment
      sliceMap[slice] = sliceMap.getOrDefault(slice, 0) + increment
    }
    endTime = startTime

    // Skip entries from before date range
    if (entry.startTime <= rangeStart) {
      break
    }
  }

  // Consolidate small slices into "Other" slice
  val totalDuration = sliceMap.values.sum()
  val sliceList =
    sliceMap
      .toList()
      .sortedByDescending { it.second }
      .takeWhile { it.second >= totalDuration * MIN_SLICE_PERCENT }
      .toMutableList()
  sliceList.add(OTHER_SLICE_NAME to totalDuration - sliceList.map { it.second }.sum())
  val nonOtherSlices = sliceList.map { it.first }.toSet()
  val bucketsWithOther = buckets.mapValues { (_, value) ->
    val (matching, other) = value.entries.partition { it.key in nonOtherSlices }
    matching.associate { it.key to it.value } + (OTHER_SLICE_NAME to other.sumOf { it.value })
  }

  return Pair(bucketsWithOther, sliceList)
}

/**
 * Counts each activity (or group, when [ChartConfig.grouped]) over entries starting within
 * [rangeStart, rangeEnd), returned as (slice, count) pairs sorted by descending count. Per [ChartConfig.countMetric]
 * the count is either the raw number of recordings ([CountMetric.OCCURRENCES]) or the number of
 * distinct local days on which it was recorded ([CountMetric.UNIQUE_DAYS]). Either way an entry is
 * attributed to the single day it started on.
 */
fun activityCounts(
  config: Config,
  history: History,
  chartConfig: ChartConfig,
  rangeStart: Long,
  rangeEnd: Long,
): List<Pair<String, Int>> {
  val uniqueDays = chartConfig.countMetric == CountMetric.UNIQUE_DAYS
  // Per slice, collect a set of distinct keys then count them: for UNIQUE_DAYS the key is the
  // entry's start-day midnight (so same-day recordings collapse to one), and for OCCURRENCES it's
  // the entry's own start time, which is unique per entry so every recording counts once
  val keySets = mutableMapOf<String, MutableSet<Long>>()
  for (entry in history.entries) {
    if (entry.startTime < rangeStart || entry.startTime >= rangeEnd) continue
    val slice = if (chartConfig.grouped) config.getActivityGroup(entry.activity) else entry.activity
    val key = if (uniqueDays) getPreviousMidnight(entry.startTime) else entry.startTime
    keySets.getOrPut(slice) { mutableSetOf() }.add(key)
  }
  return keySets.mapValues { it.value.size }.toList().sortedByDescending { it.second }
}

/**
 * Result of [buildCorrelationMatrix]: parallel [labels] alongside an NxN [values] grid where cell
 * [i][j] holds the Pearson correlation of daily durations between activities i and j (NaN when
 * undefined, i.e. either activity has no day-to-day variance)
 */
class CorrelationMatrix(val labels: List<String>, val values: Array<DoubleArray>)

/** Pearson correlation coefficient of two equal-length series, or NaN if either has no variance */
fun pearson(x: List<Double>, y: List<Double>): Double {
  val meanX = x.average()
  val meanY = y.average()
  var sumXY = 0.0
  var sumXX = 0.0
  var sumYY = 0.0
  for (i in x.indices) {
    val dx = x[i] - meanX
    val dy = y[i] - meanY
    sumXY += dx * dy
    sumXX += dx * dx
    sumYY += dy * dy
  }
  // Zero variance on either axis leaves correlation undefined (e.g. an activity never recorded)
  val denominator = sqrt(sumXX * sumYY)
  return if (denominator == 0.0) Double.NaN else sumXY / denominator
}

/**
 * Builds a Pearson-correlation matrix of daily activity durations over
 * [rangeStart, rangeEnd), reusing [aggregateEntries]'s per-day buckets and filtered slice list
 * (dropping the "Other" bucket). Each recorded day is one observation; an activity not done that
 * day counts as 0 seconds.
 */
fun buildCorrelationMatrix(
  config: Config,
  history: History,
  chartConfig: ChartConfig,
  rangeStart: Long,
  rangeEnd: Long,
): CorrelationMatrix {
  val (buckets, sliceList) =
    aggregateEntries(config, history, chartConfig, rangeStart, rangeEnd, Aggregation.DAY)
  val labels = sliceList.map { it.first }.filter { it != OTHER_SLICE_NAME }
  val days = buckets.keys.sorted()
  val series = labels.map { label -> days.map { (buckets[it]?.get(label) ?: 0L).toDouble() } }
  val values =
    Array(labels.size) { i ->
      DoubleArray(labels.size) { j -> if (i == j) 1.0 else pearson(series[i], series[j]) }
    }
  return CorrelationMatrix(labels, values)
}

// ─── Shared chart UI helpers ──────────────────────────────────────────────────────

@Composable
fun chartTypeface(): Typeface = remember { ResourcesCompat.getFont(App.ctx, R.font.exo2_regular)!! }

/** Apply the chart legend styling shared by the area and radar charts */
fun Legend.applyStyle(font: Typeface) {
  isWordWrapEnabled = true
  textColor = CHART_LABEL_COLOR
  textSize = CHART_LABEL_FONT_SIZE
  typeface = font
  xEntrySpace = 15f
}

/**
 * Toggles activity grouping, warning via toast when grouping is enabled but no groups exist yet so
 * the user knows why the chart looks unchanged
 */
private fun toggleGrouping(viewModel: MainViewModel, config: Config?, grouped: Boolean) {
  if (grouped && config?.hasGroups != true) {
    App.toast("You haven't defined any groups yet in Settings!")
  }
  viewModel.dispatch(Action.SetChartGrouping(grouped))
}

/** The "Group activities" checkbox shared by every chart's controls row */
@Composable
fun GroupActivitiesCheckbox(
  viewModel: MainViewModel,
  config: Config?,
  chartConfig: ChartConfig,
  modifier: Modifier = Modifier,
) {
  AppCheckbox(
    checked = chartConfig.grouped,
    onCheckedChange = { toggleGrouping(viewModel, config, it) },
    label = "Group activities",
    modifier = modifier,
  )
}

/** Trim [text] with a trailing ellipsis until it fits within [maxW] when measured by [paint] */
fun fit(paint: Paint, text: String, maxW: Float): String {
  if (paint.measureText(text) <= maxW) return text
  var s = text
  while (s.isNotEmpty() && paint.measureText("$s…") > maxW) s = s.dropLast(1)
  return "$s…"
}

/**
 * Canvas-chart counterpart to [ChartScaffold]: caches [compute]'s result, recomputing only when the
 * inputs change, and returns null until data is ready (config/history loaded and the range
 * non-empty)
 */
@Composable
fun <T> rememberChartData(
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
  compute: (config: Config, history: History, rangeStart: Long, rangeEnd: Long) -> T,
): T? =
  remember(config, history, chartConfig) {
    if (config == null || history == null) return@remember null
    val (rangeStart, rangeEnd) = getChartRange(history, chartConfig)
    if (rangeEnd - rangeStart <= 0) return@remember null
    compute(config, history, rangeStart, rangeEnd)
  }

/**
 * Shared envelope for the AndroidView-based chart tabs (pie, area, radar): hosts the chart
 * [factory] view above a [controls] row and runs [render] only once data is ready (config/history
 * loaded and the selected range non-empty), handing it the resolved non-null values plus the range
 * bounds
 */
@Composable
fun <T : View> ChartScaffold(
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
  chartPadding: Modifier,
  factory: (Context) -> T,
  controls: @Composable () -> Unit,
  render: (chart: T, config: Config, history: History, rangeStart: Long, rangeEnd: Long) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier.fillMaxWidth().weight(1f).then(chartPadding),
      factory = factory,
      update = { chart ->
        if (config == null || history == null) return@AndroidView
        val (rangeStart, rangeEnd) = getChartRange(history, chartConfig)
        if (rangeEnd - rangeStart <= 0) {
          App.toast("No data to show!")
          return@AndroidView
        }
        render(chart, config, history, rangeStart, rangeEnd)
      },
    )
    controls()
  }
}
