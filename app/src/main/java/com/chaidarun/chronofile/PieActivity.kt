package com.chaidarun.chronofile

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.android.synthetic.main.activity_pie.*
import org.jetbrains.anko.collections.forEachReversedByIndex

class PieActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_pie)

    with(chart) {
      description.isEnabled = false
      legend.isEnabled = false
      setTransparentCircleAlpha(0)
      setDrawEntryLabels(true)
    }
    setData()
  }

  private fun setData() {
    // Get data
    val seconds = mutableMapOf<String, Long>()
    with(App.instance.history) {
      var endTime = currentActivityStartTime
      entries.forEachReversedByIndex {
        seconds[it.activity] = seconds.getOrDefault(it.activity, 0) + endTime - it.startTime
        endTime = it.startTime
      }
    }
    val pieEntries = seconds.entries.sortedByDescending { it.value }.map { PieEntry(it.value.toFloat(), it.key) }

    // Show data
    val pieDataSet = PieDataSet(pieEntries, "Time").apply {
      colors = ColorTemplate.VORDIPLOM_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList()
      valueTextSize = 12f
      valueFormatter = IValueFormatter { value, _, _, _ -> formatSeconds(value.toLong()) }
    }
    chart.data = PieData(pieDataSet)
    chart.invalidate()
  }

  /** Pretty-prints time in seconds, e.g. 86461 -> "1d 1m" */
  private fun formatSeconds(seconds: Long): String {
    val pieces = mutableListOf<String>()
    val totalMinutes = seconds / 60
    val minutes = totalMinutes % 60
    if (minutes != 0L) {
      pieces.add(0, "${minutes}m")
    }
    val totalHours = totalMinutes / 60
    val hours = totalHours % 24
    if (hours != 0L) {
      pieces.add(0, "${hours}h")
    }
    val days = totalHours / 24
    if (days != 0L) {
      pieces.add(0, "${days}d")
    }
    return pieces.joinToString(" ")
  }
}
