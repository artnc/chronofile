// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.util.Log
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry as ChartEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DateFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private enum class PresetRange(val text: String, val duration: Long) {
  TODAY("Today", DAY_SECONDS),
  PAST_WEEK("Past week", 7 * DAY_SECONDS),
  PAST_MONTH("Past month", 30 * DAY_SECONDS),
  ALL_TIME("All time", Long.MAX_VALUE),
}

private enum class GraphTab(val title: String) {
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

  // Apply the default preset range the first time history becomes available; on cold start
  // the hydrate runs async and history is null when this screen first composes.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphDatePickerDialog(
  initialSeconds: Long,
  onPick: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  // The Material3 picker speaks UTC-midnight millis; translate to/from the local calendar date so
  // a picked day maps to that day's local midnight (matching the old GregorianCalendar behavior)
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
  setDrawAxisLine(false)
  setDrawGridLines(false)
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
 * Shared envelope for the three chart tabs: hosts the MPAndroidChart [factory] view above a
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
        setDrawEntryLabels(false)
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
          onCheckedChange = { viewModel.dispatch(Action.SetGraphGrouping(it)) },
          label = "Group similar",
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
    val pieEntries = sliceList.map { (key, value) -> PieEntry(value.toFloat(), key) }
    val metric = graphConfig.metric
    val pieDataSet =
      PieDataSet(pieEntries, "Time").apply {
        colors = CHART_COLORS
        valueLineColor = AndroidColor.TRANSPARENT
        valueLinePart1Length = 0.45f
        valueLinePart2Length = 0f
        valueTextColor = CHART_LABEL_COLOR
        valueTextSize = CHART_LABEL_FONT_SIZE
        valueTypeface = typeface
        valueFormatter =
          object : ValueFormatter() {
            override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
              val num =
                when (metric) {
                  Metric.AVERAGE -> formatDuration(value.toLong() * DAY_SECONDS / rangeSeconds)
                  Metric.TOTAL -> formatDuration(value.toLong())
                }
              return "${pieEntry?.label}: $num"
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
          onCheckedChange = { viewModel.dispatch(Action.SetGraphGrouping(it)) },
          label = "Group similar",
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
    val dataSets = groups.mapIndexed { i, group ->
      LineDataSet(lines[group], group).apply {
        val mColor = CHART_COLORS[i % CHART_COLORS.size].apply { setCircleColor(this) }
        axisDependency = YAxis.AxisDependency.LEFT
        color = mColor
        lineWidth = if (stacked) 0f else 1f
        fillAlpha = if (stacked) 255 else 0
        fillColor = mColor
        fillFormatter = IFillFormatter { _, _ -> chart.axisLeft.axisMinimum }
        setDrawCircles(false)
        setDrawCircleHole(false)
        setDrawFilled(true)
        setDrawHorizontalHighlightIndicator(false)
        setDrawValues(false)
        setDrawVerticalHighlightIndicator(false)
      }
    }
    with(chart.axisLeft) {
      axisMaximum = if (stacked) DAY_SECONDS.toFloat() else maxEntrySeconds.toFloat()
      removeAllLimitLines()
      if (!stacked) addLimitLine(areaLimitLine)
    }
    chart.data = LineData(dataSets)
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
        setDrawWeb(false)
        setTouchEnabled(false)
        xAxis.run {
          valueFormatter =
            object : ValueFormatter() {
              override fun getAxisLabel(value: Float, axis: AxisBase?) =
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
        onCheckedChange = { viewModel.dispatch(Action.SetGraphGrouping(it)) },
        label = "Group similar",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val (buckets, sliceList) =
      aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.DAY_OF_WEEK)
    val radarDataSets = sliceList.mapIndexed { i, (slice, _) ->
      val radarEntries =
        (1L until 8L).map { dayOfWeek ->
          val seconds = buckets.getOrDefault(dayOfWeek, emptyMap()).getOrDefault(slice, 0)
          RadarEntry(sqrt(seconds.toDouble()).toFloat())
        }
      RadarDataSet(radarEntries, slice).apply {
        color = CHART_COLORS[i % CHART_COLORS.size]
        setDrawValues(false)
      }
    }
    chart.data = RadarData(radarDataSets)
    chart.invalidate()
  }
}

// ─── Shared bits ─────────────────────────────────────────────────────────────

@Composable
private fun AppCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null,
      colors =
        CheckboxDefaults.colors(
          checkedColor = ColorAccent,
          uncheckedColor = Color.White,
          checkmarkColor = ColorPrimaryDark,
        ),
    )
    Spacer(Modifier.width(4.dp))
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AppRadio(selected: Boolean, onClick: () -> Unit, label: String) {
  Row(
    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(
      selected = selected,
      onClick = null,
      colors =
        RadioButtonDefaults.colors(selectedColor = ColorAccent, unselectedColor = Color.White),
    )
    Spacer(Modifier.width(4.dp))
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
  }
}
