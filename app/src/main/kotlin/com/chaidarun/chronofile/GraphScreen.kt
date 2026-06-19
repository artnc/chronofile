// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.appdev.charting.charts.LineChart
import info.appdev.charting.charts.PieChart
import info.appdev.charting.charts.RadarChart
import info.appdev.charting.components.AxisBase
import info.appdev.charting.components.Legend
import info.appdev.charting.components.LimitLine
import info.appdev.charting.components.YAxis
import info.appdev.charting.data.EntryFloat as ChartEntry
import info.appdev.charting.data.LineData
import info.appdev.charting.data.LineDataSet
import info.appdev.charting.data.PieData
import info.appdev.charting.data.PieDataSet
import info.appdev.charting.data.PieEntryFloat
import info.appdev.charting.data.RadarData
import info.appdev.charting.data.RadarDataSet
import info.appdev.charting.data.RadarEntryFloat
import info.appdev.charting.formatter.IAxisValueFormatter
import info.appdev.charting.formatter.IFillFormatter
import info.appdev.charting.formatter.IValueFormatter
import info.appdev.charting.interfaces.dataprovider.LineDataProvider
import info.appdev.charting.interfaces.datasets.ILineDataSet
import info.appdev.charting.interfaces.datasets.IRadarDataSet
import info.appdev.charting.utils.ViewPortHandler
import java.text.DateFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private enum class PresetRange(val text: String, val duration: Long) {
  TODAY("Today", DAY_SECONDS),
  PAST_WEEK("Past week", 7 * DAY_SECONDS),
  PAST_MONTH("Past month", 30 * DAY_SECONDS),
  PAST_YEAR("Past year", 365 * DAY_SECONDS),
  ALL_TIME("All time", Long.MAX_VALUE),
}

private enum class GraphTab(val title: String) {
  CORRELATION("Correlation"),
  RADAR("Radar"),
  PIE("Pie"),
  AREA("Area"),
}

// Locale-constant short weekday names (Sun..Sat); cached so the radar axis formatter doesn't
// re-allocate DateFormatSymbols on every label draw
private val SHORT_WEEKDAYS = DateFormatSymbols(Locale.US).shortWeekdays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(viewModel: MainViewModel, onNavigateUp: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val config = state.config
  val history = state.history
  val graphConfig = state.graphConfig
  var showPresetDialog by remember { mutableStateOf(false) }
  // Which date-range endpoint the picker is editing (true = start, false = end, null = closed)
  var datePickerForStart by remember { mutableStateOf<Boolean?>(null) }

  // Apply the default preset range the first time history becomes available; on cold start the
  // hydrate runs async and history is null when this screen first composes.
  LaunchedEffect(history) {
    if (history != null && graphConfig.startTime == null && graphConfig.endTime == null) {
      setPresetRange(viewModel, history, PresetRange.PAST_MONTH)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Statistics") },
        navigationIcon = {
          IconButton(onClick = onNavigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = ColorPrimary,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
          ),
      )
    },
    containerColor = ColorPrimaryDark,
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      val pagerState =
        rememberPagerState(initialPage = GraphTab.PIE.ordinal) { GraphTab.entries.size }
      val scope = rememberCoroutineScope()
      PrimaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = ColorPrimary,
        contentColor = Color.White,
        // Drop the default divider, which renders as a gray line (unset outlineVariant) below tabs
        divider = {},
      ) {
        GraphTab.entries.forEachIndexed { index, tab ->
          Tab(
            selected = pagerState.currentPage == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = { Text(tab.title) },
            selectedContentColor = Color.White,
            unselectedContentColor = Color.White.copy(alpha = 0.6f),
          )
        }
      }
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
        when (GraphTab.entries[page]) {
          GraphTab.CORRELATION -> CorrelationScreen(viewModel, config, history, graphConfig)
          GraphTab.PIE -> PieScreen(viewModel, config, history, graphConfig)
          GraphTab.AREA -> AreaScreen(viewModel, config, history, graphConfig)
          GraphTab.RADAR -> RadarScreen(viewModel, config, history, graphConfig)
        }
      }
      DateRangeBar(
        graphConfig = graphConfig,
        onPickStart = { datePickerForStart = true },
        onPickEnd = { datePickerForStart = false },
        onPickPreset = { showPresetDialog = true },
      )
    }
  }

  if (showPresetDialog) {
    PresetRangeDialog(
      onPick = { range ->
        history?.let { setPresetRange(viewModel, it, range) }
        showPresetDialog = false
      },
      onDismiss = { showPresetDialog = false },
    )
  }

  datePickerForStart?.let { isStart ->
    GraphDatePickerDialog(
      initialSeconds =
        (if (isStart) graphConfig.startTime else graphConfig.endTime) ?: epochSeconds(),
      onPick = { picked ->
        if (isStart) viewModel.dispatch(Action.SetGraphRangeStart(picked))
        else viewModel.dispatch(Action.SetGraphRangeEnd(picked))
        datePickerForStart = null
      },
      onDismiss = { datePickerForStart = null },
    )
  }
}

@Composable
private fun GraphDatePickerDialog(
  initialSeconds: Long,
  onPick: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  // The Material3 picker speaks UTC-midnight millis; translate to/from the local calendar date so a
  // picked day maps to that day's local midnight (matching the old GregorianCalendar behavior)
  val initialMillis =
    remember(initialSeconds) {
      getDate(initialSeconds).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
  val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          pickerState.selectedDateMillis?.let { millis ->
            onPick(
              Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
            )
          }
        }
      ) {
        Text("OK")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  ) {
    DatePicker(state = pickerState)
  }
}

private fun setPresetRange(viewModel: MainViewModel, history: History, presetRange: PresetRange) {
  Log.i(TAG, "Setting range to $presetRange")
  val now = history.currentActivityStartTime
  val startTime = maxOf(now - presetRange.duration, history.entries.getOrNull(0)?.startTime ?: 0)
  viewModel.dispatch(Action.SetGraphRangeStart(startTime))
  viewModel.dispatch(Action.SetGraphRangeEnd(now))
}

@Composable
private fun chartTypeface(): Typeface = remember {
  ResourcesCompat.getFont(App.ctx, R.font.exo2_regular)!!
}

/** Hide an axis line, its gridlines, and its labels */
private fun AxisBase.hide() {
  isDrawAxisLine = false
  isDrawGridLines = false
  setDrawLabels(false)
}

/** Apply the chart legend styling shared by the area and radar charts */
private fun Legend.applyStyle(font: Typeface) {
  isWordWrapEnabled = true
  textColor = CHART_LABEL_COLOR
  textSize = CHART_LABEL_FONT_SIZE
  typeface = font
  xEntrySpace = 15f
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
  clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
  )

@Composable
private fun DateRangeBar(
  graphConfig: GraphConfig,
  onPickStart: () -> Unit,
  onPickEnd: () -> Unit,
  onPickPreset: () -> Unit,
) {
  val buttonColors =
    ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = Color.White)
  Row(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Button(
      onClick = onPickStart,
      shape = ButtonShape,
      colors = buttonColors,
      modifier = Modifier.weight(1f),
    ) {
      Text(graphConfig.startTime?.let { formatDate(it) } ?: "—")
    }
    Text(
      "to",
      color = Color.White,
      modifier = Modifier.padding(horizontal = 12.dp).clickableNoRipple(onPickPreset),
    )
    Button(
      onClick = onPickEnd,
      shape = ButtonShape,
      colors = buttonColors,
      modifier = Modifier.weight(1f),
    ) {
      Text(graphConfig.endTime?.let { formatDate(it) } ?: "—")
    }
  }
}

@Composable
private fun PresetRangeDialog(onPick: (PresetRange) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quick range") },
    text = {
      Column {
        PresetRange.entries.forEach { range ->
          // Tapping a range applies it immediately (no separate confirm step)
          Text(
            range.text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable { onPick(range) }.padding(vertical = 12.dp),
          )
        }
      }
    },
    // No confirm button: tapping a range commits immediately
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    containerColor = ColorPrimaryDark,
  )
}

/**
 * Shared envelope for the three chart tabs: hosts the AndroidChart [factory] view above a
 * [controls] row and runs [render] only once data is ready (config/history loaded and the selected
 * range non-empty), handing it the resolved non-null values plus the range bounds
 */
@Composable
private fun <T : View> ChartScaffold(
  config: Config?,
  history: History?,
  graphConfig: GraphConfig,
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
        val (rangeStart, rangeEnd) = getChartRange(history, graphConfig)
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

/**
 * Toggles activity grouping, warning via toast when grouping is enabled but no groups exist yet so
 * the user knows why the chart looks unchanged
 */
private fun toggleGrouping(viewModel: MainViewModel, config: Config?, grouped: Boolean) {
  if (grouped && config?.hasGroups != true) {
    App.toast("You haven't defined any groups yet in Settings!")
  }
  viewModel.dispatch(Action.SetGraphGrouping(grouped))
}

// ─── Pie chart ────────────────────────────────────────────────────────────────

@Composable
private fun PieScreen(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  graphConfig: GraphConfig,
) {
  val typeface = chartTypeface()
  ChartScaffold(
    config = config,
    history = history,
    graphConfig = graphConfig,
    chartPadding = Modifier.padding(horizontal = 8.dp),
    factory = {
      PieChart(it).apply {
        description.isEnabled = false
        holeRadius = 50f
        legend.isEnabled = false
        rotationAngle = 225f
        setCenterTextColor(CHART_LABEL_COLOR)
        setCenterTextSize(CHART_LABEL_FONT_SIZE)
        setCenterTextTypeface(typeface)
        isDrawEntryLabels = false
        setExtraOffsets(50f, 0f, 50f, 0f)
        setHoleColor(AndroidColor.TRANSPARENT)
        setTouchEnabled(false)
        setTransparentCircleAlpha(0)
      }
    },
    controls = {
      Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        AppCheckbox(
          checked = graphConfig.grouped,
          onCheckedChange = { toggleGrouping(viewModel, config, it) },
          label = "Group activities",
          modifier = Modifier.weight(1f),
        )
        Column(modifier = Modifier.weight(1f)) {
          AppRadio(
            selected = graphConfig.metric == Metric.AVERAGE,
            onClick = { viewModel.dispatch(Action.SetGraphMetric(Metric.AVERAGE)) },
            label = "Average daily",
          )
          AppRadio(
            selected = graphConfig.metric == Metric.TOTAL,
            onClick = { viewModel.dispatch(Action.SetGraphMetric(Metric.TOTAL)) },
            label = "Total recorded",
          )
        }
      }
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val rangeSeconds = rangeEnd - rangeStart
    val (_, sliceList) =
      aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.TOTAL)
    val pieEntries =
      sliceList
        .map { (key, value) -> PieEntryFloat(value.toFloat()).apply { label = key } }
        .toMutableList()
    val metric = graphConfig.metric
    val pieDataSet =
      PieDataSet(pieEntries, "Time").apply {
        setColors(CHART_COLORS.toMutableList())
        valueLineColor = AndroidColor.TRANSPARENT
        valueLinePart1Length = 0.45f
        valueLinePart2Length = 0f
        valueTextColor = CHART_LABEL_COLOR
        valueTextSize = CHART_LABEL_FONT_SIZE
        valueTypeface = typeface
        valueFormatter =
          object : IValueFormatter {
            // Pie passes the slice's y-value (seconds); entry carries the label
            override fun getFormattedValue(
              value: Float,
              entryFloat: ChartEntry?,
              dataSetIndex: Int,
              viewPortHandler: ViewPortHandler?,
            ): String {
              val num =
                when (metric) {
                  Metric.AVERAGE -> formatDuration(value.toLong() * DAY_SECONDS / rangeSeconds)
                  Metric.TOTAL -> formatDuration(value.toLong())
                }
              return "${(entryFloat as? PieEntryFloat)?.label}: $num"
            }
          }
        yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
      }
    chart.centerText =
      "Range:\n${formatDuration(sliceList.map { it.second }.sum(), showDays = true)}"
    chart.data = PieData(pieDataSet)
    chart.invalidate()
  }
}

// ─── Area chart ───────────────────────────────────────────────────────────────

@Composable
private fun AreaScreen(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  graphConfig: GraphConfig,
) {
  val font = chartTypeface()
  ChartScaffold(
    config = config,
    history = history,
    graphConfig = graphConfig,
    chartPadding = Modifier.padding(start = 2.dp, top = 32.dp, end = 2.dp, bottom = 8.dp),
    factory = {
      LineChart(it).apply {
        with(axisLeft) {
          axisMinimum = 0f
          hide()
        }
        axisRight.isEnabled = false
        description.isEnabled = false
        legend.applyStyle(font)
        setDrawBorders(false)
        setDrawGridBackground(false)
        xAxis.hide()
        setTouchEnabled(false)
      }
    },
    controls = {
      Row(modifier = Modifier.fillMaxWidth()) {
        AppCheckbox(
          checked = graphConfig.grouped,
          onCheckedChange = { toggleGrouping(viewModel, config, it) },
          label = "Group activities",
          modifier = Modifier.weight(1f).padding(16.dp),
        )
        AppCheckbox(
          checked = graphConfig.stacked,
          onCheckedChange = { viewModel.dispatch(Action.SetGraphStacking(it)) },
          label = "Stack activities",
          modifier = Modifier.weight(1f).padding(16.dp),
        )
      }
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val (buckets, sliceList) =
      aggregateEntries(
        config,
        history,
        graphConfig,
        getPreviousMidnight(rangeStart),
        rangeEnd,
        Aggregation.DAY,
      )
    val groups = sliceList.map { it.first }
    val lines = groups.associateWith { mutableListOf<ChartEntry>() }
    val groupsReversed = groups.reversed()
    val stacked = graphConfig.stacked
    var maxEntrySeconds = 0L
    for ((dayStart, dayGroups) in buckets.toList().sortedBy { it.first }) {
      var seenSecondsToday = 0L
      for (group in groupsReversed) {
        val seconds = dayGroups.getOrDefault(group, 0L)
        seenSecondsToday += seconds
        maxEntrySeconds = maxOf(maxEntrySeconds, seconds)
        val entrySeconds = if (stacked) seenSecondsToday else seconds
        lines[group]?.add(ChartEntry(dayStart.toFloat(), entrySeconds.toFloat()))
          ?: error("$group missing from area chart data sets")
      }
    }
    val dataSets: List<ILineDataSet<ChartEntry>> = groups.mapIndexed { i, group ->
      LineDataSet<ChartEntry>(lines.getValue(group), group).apply {
        val mColor = CHART_COLORS[i % CHART_COLORS.size].apply { setCircleColor(this) }
        axisDependency = YAxis.AxisDependency.LEFT
        color = mColor
        lineWidth = if (stacked) 0f else 1f
        fillAlpha = if (stacked) 255 else 0
        fillColor = mColor
        fillFormatter =
          object : IFillFormatter {
            override fun getFillLinePosition(
              dataSet: ILineDataSet<*>?,
              dataProvider: LineDataProvider,
            ) = chart.axisLeft.axisMinimum
          }
        isDrawCircles = false
        isDrawCircleHoleEnabled = false
        isDrawFilled = true
        isHorizontalHighlightIndicator = false
        isDrawValues = false
        isVerticalHighlightIndicator = false
      }
    }
    with(chart.axisLeft) {
      axisMaximum = if (stacked) DAY_SECONDS.toFloat() else maxEntrySeconds.toFloat()
      removeAllLimitLines()
      if (!stacked) addLimitLine(areaLimitLine)
    }
    chart.data = LineData(dataSets.toMutableList())
    chart.isScaleYEnabled = !stacked
    graphConfig.startTime?.toFloat()?.let { chart.moveViewToX(it) }
    chart.invalidate()
  }
}

// Dashed reference line at the 8-hour mark
private val areaLimitLine =
  LimitLine(8 * 60 * 60f).apply {
    lineColor = AndroidColor.WHITE
    lineWidth = 2f
    enableDashedLine(5f, 5f, 0f)
  }

// ─── Radar chart ──────────────────────────────────────────────────────────────

@Composable
private fun RadarScreen(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  graphConfig: GraphConfig,
) {
  val font = chartTypeface()
  ChartScaffold(
    config = config,
    history = history,
    graphConfig = graphConfig,
    chartPadding = Modifier.padding(start = 8.dp, top = 32.dp, end = 8.dp, bottom = 8.dp),
    factory = {
      RadarChart(it).apply {
        description.isEnabled = false
        legend.applyStyle(font)
        // 5.x dropped setDrawWeb; hide the web by zeroing its alpha. A transparent webColor won't
        // work: the renderer overrides the color's alpha with webAlpha, so transparent (0x00000000)
        // would draw as opaque black.
        webAlpha = 0
        setTouchEnabled(false)
        xAxis.run {
          valueFormatter =
            object : IAxisValueFormatter {
              override fun getFormattedValue(value: Float, axis: AxisBase?) =
                SHORT_WEEKDAYS[((value.toInt() + 1) % 7) + 1]
            }
          textColor = CHART_LABEL_COLOR
          textSize = CHART_LABEL_FONT_SIZE
          typeface = font
        }
        yAxis.run {
          axisMinimum = 0f
          setDrawLabels(false)
        }
      }
    },
    controls = {
      AppCheckbox(
        checked = graphConfig.grouped,
        onCheckedChange = { toggleGrouping(viewModel, config, it) },
        label = "Group activities",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val (buckets, sliceList) =
      aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.DAY_OF_WEEK)
    val radarDataSets: List<IRadarDataSet> = sliceList.mapIndexed { i, (slice, _) ->
      val radarEntries =
        (1L until 8L)
          .map { dayOfWeek ->
            val seconds = buckets.getOrDefault(dayOfWeek, emptyMap()).getOrDefault(slice, 0)
            RadarEntryFloat(sqrt(seconds.toDouble()).toFloat())
          }
          .toMutableList()
      RadarDataSet(radarEntries, slice).apply {
        color = CHART_COLORS[i % CHART_COLORS.size]
        isDrawValues = false
      }
    }
    chart.data = RadarData(radarDataSets.toMutableList())
    chart.invalidate()
  }
}

// ─── Correlation matrix ─────────────────────────────────────────────────────────

@Composable
private fun CorrelationScreen(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  graphConfig: GraphConfig,
) {
  val typeface = chartTypeface()
  // Recompute only when inputs change; null until data is ready or the selected range is empty
  val matrix =
    remember(config, history, graphConfig) {
      if (config == null || history == null) return@remember null
      val (rangeStart, rangeEnd) = getChartRange(history, graphConfig)
      if (rangeEnd - rangeStart <= 0) return@remember null
      buildCorrelationMatrix(config, history, graphConfig, rangeStart, rangeEnd)
    }
  Column(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
      matrix?.let { drawCorrelationMatrix(it, typeface) }
    }
    AppCheckbox(
      checked = graphConfig.grouped,
      onCheckedChange = { toggleGrouping(viewModel, config, it) },
      label = "Group activities",
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
  }
}

/** Signed two-decimal correlation label with the leading zero stripped, e.g. "+.42", "-1.00" */
private fun formatCorr(r: Double): String {
  val pct = (abs(r) * 100).roundToInt()
  return (if (r < 0) "-" else "+") + (if (pct >= 100) "1.00" else ".%02d".format(pct))
}

/**
 * Draws [matrix] as a square grid of cells colored from dark teal (the lowest correlation) through
 * to light green (the highest), each labeled with its value, with activity names down the left edge
 * and the same names rotated 90° clockwise along the top edge
 */
private fun DrawScope.drawCorrelationMatrix(matrix: CorrelationMatrix, typeface: Typeface) {
  val n = matrix.labels.size
  if (n == 0) return
  val pad = 4.dp.toPx()

  // Configure the right-aligned axis label paint and reserve gutters sized to the longest label
  val labelPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = CHART_LABEL_COLOR
      this.typeface = typeface
      textAlign = Paint.Align.RIGHT
      textSize = 11.sp.toPx()
    }
  val fm = labelPaint.fontMetrics
  val longest = matrix.labels.maxOf { labelPaint.measureText(it) } + pad
  val gutterW = minOf(longest, size.width * 0.3f)
  val topH = minOf(longest, size.height * 0.3f)
  val cell = minOf((size.width - gutterW) / n, (size.height - topH) / n)
  if (cell <= 0f) return
  val gridLeft = gutterW
  // Center the labels-plus-grid block vertically so the gap above its top labels equals the gap
  // below its bottom row
  val gridTop = topH + (size.height - topH - n * cell) / 2

  // Trim a label with a trailing ellipsis so it fits within maxW
  fun fit(text: String, maxW: Float): String {
    if (labelPaint.measureText(text) <= maxW) return text
    var s = text
    while (s.isNotEmpty() && labelPaint.measureText("$s…") > maxW) s = s.dropLast(1)
    return "$s…"
  }

  // Size the value paint so the widest possible label ("+1.00") fits inside a cell
  val valuePaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.typeface = typeface
      textAlign = Paint.Align.CENTER
      textSize = cell * 0.3f
    }
  while (valuePaint.textSize > 6f && valuePaint.measureText("+1.00") > cell * 0.86f) {
    valuePaint.textSize -= 1f
  }
  val vfm = valuePaint.fontMetrics
  // Baseline shift to vertically center text on a point (loop-invariant for both paints)
  val valueBaseline = (vfm.ascent + vfm.descent) / 2
  val labelBaseline = (fm.ascent + fm.descent) / 2
  val canvas = drawContext.canvas.nativeCanvas

  // Scale the colors to the actual off-diagonal correlation range; the diagonal's trivial 1.00
  // self-correlations are excluded so they don't peg the maximum (they clamp to the greenest cell)
  var minR = Double.POSITIVE_INFINITY
  var maxR = Double.NEGATIVE_INFINITY
  for (i in 0 until n) {
    for (j in 0 until n) {
      val r = matrix.values[i][j]
      if (i == j || r.isNaN()) continue
      if (r < minR) minR = r
      if (r > maxR) maxR = r
    }
  }
  val span = maxR - minR

  // Draw cells: fill by correlation, then overlay the value in a contrasting color (NaN = blank)
  for (i in 0 until n) {
    for (j in 0 until n) {
      val r = matrix.values[i][j]
      if (r.isNaN()) continue
      val left = gridLeft + j * cell
      val top = gridTop + i * cell
      val fraction = if (span > 0) ((r - minR) / span).coerceIn(0.0, 1.0).toFloat() else 0.5f
      val color = lerp(ColorPrimaryDark, ColorAccent, fraction)
      drawRect(color, topLeft = Offset(left, top), size = Size(cell, cell))
      // White text on the darker (negative) cells, dark text on the lighter green (positive) ones
      valuePaint.color = (if (color.luminance() > 0.4f) ColorPrimaryDark else Color.White).toArgb()
      canvas.drawText(formatCorr(r), left + cell / 2, top + cell / 2 - valueBaseline, valuePaint)
    }
  }

  // Overlay faint gridlines to separate adjacent same-colored cells
  val gridColor = Color.White.copy(alpha = 0.12f)
  for (k in 0..n) {
    val offset = k * cell
    drawLine(
      gridColor,
      Offset(gridLeft + offset, gridTop),
      Offset(gridLeft + offset, gridTop + n * cell),
    )
    drawLine(
      gridColor,
      Offset(gridLeft, gridTop + offset),
      Offset(gridLeft + n * cell, gridTop + offset),
    )
  }

  // Draw labels: activity names horizontally down the left, then rotated 90° clockwise on top
  for (i in 0 until n) {
    val rowCenter = gridTop + i * cell + cell / 2
    canvas.drawText(
      fit(matrix.labels[i], gutterW - pad),
      gutterW - pad,
      rowCenter - labelBaseline,
      labelPaint,
    )
    // Rotating the canvas 90° about the anchor turns this left-extending right-aligned text into an
    // upward column centered on the column, reading top-to-bottom
    val colCenter = gridLeft + i * cell + cell / 2
    val anchorX = colCenter + labelBaseline
    val anchorY = gridTop - pad
    canvas.save()
    canvas.rotate(90f, anchorX, anchorY)
    canvas.drawText(fit(matrix.labels[i], topH - pad), anchorX, anchorY, labelPaint)
    canvas.restore()
  }
}
