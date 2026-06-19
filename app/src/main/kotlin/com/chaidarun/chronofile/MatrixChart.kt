// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MatrixChart(
  viewModel: MainViewModel,
  config: Config?,
  history: History?,
  chartConfig: ChartConfig,
) {
  val typeface = chartTypeface()
  val matrix =
    rememberChartData(config, history, chartConfig) { c, h, rangeStart, rangeEnd ->
      buildCorrelationMatrix(c, h, chartConfig, rangeStart, rangeEnd)
    }
  Column(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxWidth().weight(1f).padding(CHART_PADDING)) {
      matrix?.let { drawCorrelationMatrix(it, typeface) }
    }
    GroupActivitiesCheckbox(viewModel, config, chartConfig, Modifier.fillMaxWidth().padding(16.dp))
  }
}

/** Signed two-decimal correlation label with the leading zero stripped, e.g. "+.42", "-1.00" */
private fun formatCorr(r: Double): String {
  val pct = (abs(r) * 100).roundToInt()
  return (if (r < 0) "-" else "+") + (if (pct >= 100) "1.00" else ".%02d".format(pct))
}

/**
 * Draws [matrix] as a square grid of cells colored on a diverging scale from light red (the most
 * negative correlation) through dark teal (zero) to light green (the most positive), each labeled
 * with its value, with activity names down the left edge and the same names rotated 90° clockwise
 * along the top edge
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

  // Find the off-diagonal correlation extremes to scale each side of the diverging color ramp; the
  // diagonal's trivial 1.00 self-correlations are excluded so they don't peg the positive maximum
  // (they clamp to the greenest cell)
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
  // Anchor zero at the dark-teal background; scale positives toward green by the largest positive
  // and negatives toward red by the largest negative, each side independently filling its full
  // ramp.
  // Precompute reciprocals so the per-cell loop multiplies (and skips the zero-guard) instead
  val invMaxPos = if (maxR > 0.0) (1.0 / maxR) else 0.0
  val invMaxNeg = if (minR < 0.0) (1.0 / -minR) else 0.0

  // Draw cells: fill by correlation, then overlay the value in a contrasting color (NaN = blank)
  for (i in 0 until n) {
    for (j in 0 until n) {
      val r = matrix.values[i][j]
      if (r.isNaN()) continue
      val left = gridLeft + j * cell
      val top = gridTop + i * cell
      val positive = r >= 0
      val fraction = (if (positive) r * invMaxPos else -r * invMaxNeg).coerceIn(0.0, 1.0).toFloat()
      val color = lerp(ColorPrimaryDark, if (positive) ColorAccent else ColorNegative, fraction)
      drawRect(color, topLeft = Offset(left, top), size = Size(cell, cell))
      // White text on the darker teal/red cells, dark text on the lighter green (positive) ones
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
      fit(labelPaint, matrix.labels[i], gutterW - pad),
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
    canvas.drawText(fit(labelPaint, matrix.labels[i], topH - pad), anchorX, anchorY, labelPaint)
    canvas.restore()
  }
}
