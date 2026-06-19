// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val COUNT_BAR_THICKNESS = 12.dp
private val COUNT_BAR_GAP = 6.dp

@Composable
fun CountChart(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
) {
  val typeface = chartTypeface()
  val counts =
    rememberChartData(config, history, chartConfig) { c, h, rangeStart, rangeEnd ->
      activityCounts(c, h, chartConfig, rangeStart, rangeEnd)
    }
  Column(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
      // Size the canvas to fit every bar so the column scrolls when they overflow, but never below
      // the viewport so a short list still centers vertically
      val n = counts?.size ?: 0
      val canvasHeight =
        maxOf(
          COUNT_BAR_THICKNESS * n + COUNT_BAR_GAP * (n - 1).coerceAtLeast(0) + CHART_PADDING * 2,
          maxHeight,
        )
      Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Canvas(modifier = Modifier.fillMaxWidth().height(canvasHeight).padding(CHART_PADDING)) {
          counts?.let { drawCountChart(it, typeface) }
        }
      }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      GroupActivitiesCheckbox(viewModel, config, chartConfig, Modifier.weight(1f))
      Column(modifier = Modifier.weight(1f)) {
        AppRadio(
          selected = chartConfig.countMetric == CountMetric.UNIQUE_DAYS,
          onClick = { viewModel.dispatch(Action.SetChartCountMetric(CountMetric.UNIQUE_DAYS)) },
          label = "Unique days",
        )
        AppRadio(
          selected = chartConfig.countMetric == CountMetric.OCCURRENCES,
          onClick = { viewModel.dispatch(Action.SetChartCountMetric(CountMetric.OCCURRENCES)) },
          label = "Total occurrences",
        )
      }
    }
  }
}

/**
 * Draws [counts] (activities paired with their counts, sorted descending) as horizontal bars: a
 * right-aligned activity name in a left gutter, a thin accent-colored bar whose length is
 * proportional to the count, and the count drawn just past the bar's end. The bar block is
 * vertically centered, which matters only when it's shorter than the canvas (the caller sizes the
 * canvas to fit every bar, so a longer list fills the canvas exactly and the column scrolls).
 */
private fun DrawScope.drawCountChart(counts: List<Pair<String, Int>>, typeface: Typeface) {
  if (counts.isEmpty()) return
  val pad = 4.dp.toPx()
  val barThickness = COUNT_BAR_THICKNESS.toPx()
  val gap = COUNT_BAR_GAP.toPx()
  val n = counts.size

  // Configure the gutter (right-aligned name) and count (left-aligned, past the bar) paints
  val labelPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = CHART_LABEL_COLOR
      this.typeface = typeface
      textAlign = Paint.Align.RIGHT
      textSize = 11.sp.toPx()
    }
  val countPaint = Paint(labelPaint).apply { textAlign = Paint.Align.LEFT }
  // Baseline shift to vertically center text on a bar's midline
  val baseline = labelPaint.fontMetrics.let { (it.ascent + it.descent) / 2 }

  // Reserve a left gutter for names (capped at 40% width) and a right margin for the widest count
  val maxCount = counts.first().second
  val gutterW = minOf(counts.maxOf { labelPaint.measureText(it.first) } + pad, size.width * 0.4f)
  val barAreaW = size.width - gutterW - (countPaint.measureText(maxCount.toString()) + pad)
  if (barAreaW <= 0f) return

  val top = (size.height - (n * barThickness + (n - 1) * gap)) / 2
  val canvas = drawContext.canvas.nativeCanvas
  counts.forEachIndexed { i, (label, count) ->
    val barTop = top + i * (barThickness + gap)
    val center = barTop + barThickness / 2
    val barW = barAreaW * count / maxCount
    drawRect(ColorAccent, topLeft = Offset(gutterW, barTop), size = Size(barW, barThickness))
    canvas.drawText(
      fit(labelPaint, label, gutterW - pad),
      gutterW - pad,
      center - baseline,
      labelPaint,
    )
    canvas.drawText(count.toString(), gutterW + barW + pad, center - baseline, countPaint)
  }
}
