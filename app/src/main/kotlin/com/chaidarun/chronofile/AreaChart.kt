// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.appdev.charting.charts.LineChart
import info.appdev.charting.components.AxisBase
import info.appdev.charting.components.LimitLine
import info.appdev.charting.components.YAxis
import info.appdev.charting.data.EntryFloat as ChartEntry
import info.appdev.charting.data.LineData
import info.appdev.charting.data.LineDataSet
import info.appdev.charting.formatter.IFillFormatter
import info.appdev.charting.interfaces.dataprovider.LineDataProvider
import info.appdev.charting.interfaces.datasets.ILineDataSet

/** Hide an axis line, its gridlines, and its labels */
private fun AxisBase.hide() {
  isDrawAxisLine = false
  isDrawGridLines = false
  setDrawLabels(false)
}

// Dashed reference line at the 8-hour mark
private val areaLimitLine =
  LimitLine(8 * 60 * 60f).apply {
    lineColor = AndroidColor.WHITE
    lineWidth = 2f
    enableDashedLine(5f, 5f, 0f)
  }

@Composable
fun AreaChart(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
) {
  val font = chartTypeface()
  ChartScaffold(
    config = config,
    history = history,
    chartConfig = chartConfig,
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
        GroupActivitiesCheckbox(viewModel, config, chartConfig, Modifier.weight(1f).padding(16.dp))
        AppCheckbox(
          checked = chartConfig.stacked,
          onCheckedChange = { viewModel.dispatch(Action.SetChartStacking(it)) },
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
        chartConfig,
        getPreviousMidnight(rangeStart),
        rangeEnd,
        Aggregation.DAY,
      )
    val groups = sliceList.map { it.first }
    val lines = groups.associateWith { mutableListOf<ChartEntry>() }
    val groupsReversed = groups.reversed()
    val stacked = chartConfig.stacked
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
    chartConfig.startTime?.toFloat()?.let { chart.moveViewToX(it) }
    chart.invalidate()
  }
}
