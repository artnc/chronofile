package com.chaidarun.chronofile

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.android.synthetic.main.activity_pie.*

class PieActivity : BaseActivity() {

  private enum class Metric { AVERAGE, PERCENTAGE, TOTAL }

  private var grouped = true
  private var metric = Metric.AVERAGE

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_pie)

    with(chart) {
      description.isEnabled = false
      holeRadius = 25f
      legend.isEnabled = false
      rotationAngle = 195f
      setDrawEntryLabels(false)
      setTransparentCircleAlpha(0)
    }
    setData()
  }

  fun onRadioButtonClicked(view: View) {
    with(view as RadioButton) {
      if (!isChecked) {
        return
      }
      when (id) {
        R.id.radioAverage -> metric = Metric.AVERAGE
        R.id.radioGrouped -> grouped = true
        R.id.radioIndividual -> grouped = false
        R.id.radioPercentage -> metric = Metric.PERCENTAGE
        R.id.radioTotal -> metric = Metric.TOTAL
      }
      setData()
    }
  }

  private fun setData() {
    // Get data
    val slices = mutableMapOf<String, Long>()
    val totalSeconds = with(App.instance.history) {
      var totalSeconds = currentActivityStartTime - entries[0].startTime

      // For daily metrics, we restrict the timeframe to as many whole days as possible
      val minStartTime = when (metric) {
        Metric.AVERAGE -> {
          totalSeconds = totalSeconds / 86400 * 86400
          currentActivityStartTime - totalSeconds
        }
        else -> 0
      }

      var endTime = currentActivityStartTime
      val config = App.instance.config
      for (entry in entries.reversed()) {
        val startTime = Math.max(entry.startTime, minStartTime)
        val seconds = endTime - startTime
        val slice = if (grouped) config.getActivityGroup(entry.activity) else entry.activity
        slices[slice] = slices.getOrDefault(slice, 0) + seconds
        if (startTime == minStartTime) {
          break
        }
        endTime = startTime
      }
      totalSeconds
    }
    val pieEntries = slices.entries.sortedByDescending { it.value }.map { PieEntry(it.value.toFloat(), it.key) }

    // Show data
    val pieDataSet = PieDataSet(pieEntries, "Time").apply {
      colors = ColorTemplate.MATERIAL_COLORS.toList()
      valueTextSize = 10f
      valueFormatter = IValueFormatter { value, entry, _, _ ->
        val num: String = when (metric) {
          Metric.AVERAGE -> formatTime(value.toLong() * 86400 / totalSeconds)
          Metric.PERCENTAGE -> "${value.toLong() * 100 / totalSeconds}%"
          Metric.TOTAL -> formatTime(value.toLong())
        }
        "${(entry as PieEntry).label}: $num"
      }
    }

    with(chart) {
      centerText = "Total:\n${formatTime(totalSeconds)}"
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
