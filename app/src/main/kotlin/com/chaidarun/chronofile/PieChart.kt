// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.appdev.charting.charts.PieChart as PieChartView
import info.appdev.charting.data.EntryFloat as ChartEntry
import info.appdev.charting.data.PieData
import info.appdev.charting.data.PieDataSet
import info.appdev.charting.data.PieEntryFloat
import info.appdev.charting.formatter.IValueFormatter
import info.appdev.charting.utils.ViewPortHandler

@Composable
fun PieChart(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
) {
  val typeface = chartTypeface()
  ChartScaffold(
    config = config,
    history = history,
    chartConfig = chartConfig,
    chartPadding = Modifier.padding(horizontal = 8.dp),
    factory = {
      PieChartView(it).apply {
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
        GroupActivitiesCheckbox(viewModel, config, chartConfig, Modifier.weight(1f))
        Column(modifier = Modifier.weight(1f)) {
          AppRadio(
            selected = chartConfig.metric == Metric.AVERAGE,
            onClick = { viewModel.dispatch(Action.SetChartMetric(Metric.AVERAGE)) },
            label = "Average daily",
          )
          AppRadio(
            selected = chartConfig.metric == Metric.TOTAL,
            onClick = { viewModel.dispatch(Action.SetChartMetric(Metric.TOTAL)) },
            label = "Total recorded",
          )
        }
      }
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val rangeSeconds = rangeEnd - rangeStart
    val (_, sliceList) =
      aggregateEntries(config, history, chartConfig, rangeStart, rangeEnd, Aggregation.TOTAL)
    val pieEntries =
      sliceList
        .map { (key, value) -> PieEntryFloat(value.toFloat()).apply { label = key } }
        .toMutableList()
    val metric = chartConfig.metric
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
