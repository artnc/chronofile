// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.appdev.charting.charts.RadarChart as RadarChartView
import info.appdev.charting.components.AxisBase
import info.appdev.charting.data.RadarData
import info.appdev.charting.data.RadarDataSet
import info.appdev.charting.data.RadarEntryFloat
import info.appdev.charting.formatter.IAxisValueFormatter
import info.appdev.charting.interfaces.datasets.IRadarDataSet
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.math.sqrt

// Locale-constant short weekday names (Sun..Sat); cached so the radar axis formatter doesn't
// re-allocate DateFormatSymbols on every label draw
private val SHORT_WEEKDAYS = DateFormatSymbols(Locale.US).shortWeekdays

@Composable
fun RadarChart(
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
    chartPadding = Modifier.padding(start = 8.dp, top = 32.dp, end = 8.dp, bottom = 8.dp),
    factory = {
      RadarChartView(it).apply {
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
      GroupActivitiesCheckbox(
        viewModel,
        config,
        chartConfig,
        Modifier.fillMaxWidth().padding(16.dp),
      )
    },
  ) { chart, config, history, rangeStart, rangeEnd ->
    val (buckets, sliceList) =
      aggregateEntries(config, history, chartConfig, rangeStart, rangeEnd, Aggregation.DAY_OF_WEEK)
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
