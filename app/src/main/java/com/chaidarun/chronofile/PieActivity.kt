package com.chaidarun.chronofile

import android.os.Bundle
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.android.synthetic.main.activity_pie.*
import org.jetbrains.anko.collections.forEachReversedByIndex

class PieActivity : BaseActivity() {

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
    val categories = mutableMapOf<String, Long>()
    var totalSeconds = 0L
    with(App.instance.history) {
      var endTime = currentActivityStartTime
      entries.forEachReversedByIndex {
        val seconds = endTime - it.startTime
        categories[it.activity] = categories.getOrDefault(it.activity, 0) + seconds
        totalSeconds += seconds
        endTime = it.startTime
      }
    }
    val pieEntries = categories.entries.sortedByDescending { it.value }.map { PieEntry(it.value.toFloat(), it.key) }

    // Show data
    val pieDataSet = PieDataSet(pieEntries, "Time").apply {
      colors = ColorTemplate.MATERIAL_COLORS.toList()
      valueTextSize = 12f
      valueFormatter = IValueFormatter { value, _, _, _ -> formatTime(value.toLong()) }
    }

    with(chart) {
      centerText = "${formatTime(totalSeconds)}\nTotal"
      data = PieData(pieDataSet)
      invalidate()
    }
  }

  /** Pretty-prints time given in seconds, e.g. 86461 -> "1d 1m" */
  private fun formatTime(seconds: Long): String {
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
