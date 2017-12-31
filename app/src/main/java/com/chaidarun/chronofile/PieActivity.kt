package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import kotlinx.android.synthetic.main.activity_pie.*

enum class Metric { AVERAGE, PERCENTAGE, TOTAL }
data class GraphSettings(val grouped: Boolean = true, val metric: Metric = Metric.AVERAGE)

class PieActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_pie)
    setSupportActionBar(pieToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    with(chart) {
      description.isEnabled = false
      holeRadius = 50f
      legend.isEnabled = false
      rotationAngle = 195f
      setCenterTextColor(Color.WHITE)
      setCenterTextTypeface(App.instance.typeface)
      setDrawEntryLabels(false)
      setExtraOffsets(40f, 40f, 40f, 40f)
      setHoleColor(Color.TRANSPARENT)
      setTransparentCircleAlpha(0)
    }

    disposables = listOf(
      Store.state.filter { it.config != null && it.history != null }
        .map { Triple(it.config!!, it.history!!, it.graphSettings) }
        .distinctUntilChanged().subscribe { update(it) }
    )
  }

  fun onRadioButtonClicked(view: View) {
    with(view as RadioButton) {
      if (!isChecked) {
        return
      }
      when (id) {
        R.id.radioAverage -> Store.dispatch(Action.SetGraphMetric(Metric.AVERAGE))
        R.id.radioGrouped -> Store.dispatch(Action.SetGraphGrouping(true))
        R.id.radioIndividual -> Store.dispatch(Action.SetGraphGrouping(false))
        R.id.radioPercentage -> Store.dispatch(Action.SetGraphMetric(Metric.PERCENTAGE))
        R.id.radioTotal -> Store.dispatch(Action.SetGraphMetric(Metric.TOTAL))
      }
    }
  }

  /** (Re-)renders pie chart */
  private fun update(state: Triple<Config, History, GraphSettings>) {
    val (config, history, graphSettings) = state
    Log.d(TAG, "Rendering pie chart")

    // Get data
    val (grouped, metric) = graphSettings
    val slices = mutableMapOf<String, Long>()
    val totalSeconds = with(history) {
      var totalSeconds = currentActivityStartTime - entries[0].startTime

      // For daily metrics, we restrict the timeframe to as many whole days as possible
      val minStartTime = when (metric) {
        Metric.AVERAGE -> {
          totalSeconds = totalSeconds / 86400 * 86400
          currentActivityStartTime - totalSeconds
        }
        else -> 0
      }

      // Bucket entries into slices
      var endTime = currentActivityStartTime
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

    // Show data
    val pieEntries = slices.entries.sortedByDescending { it.value }
      .map { PieEntry(it.value.toFloat(), it.key) }
    val pieDataSet = PieDataSet(pieEntries, "Time").apply {
      colors = listOf(
        "#66BB6A",
        "#388E3C",
        "#81C784",
        "#4CAF50",
        "#2E7D32",
        "#1B5E20",
        "#A5D6A7",
        "#43A047"
      ).map { Color.parseColor(it) }
      valueLineColor = Color.TRANSPARENT
      valueTextColor = Color.WHITE
      valueTextSize = 12f
      valueTypeface = App.instance.typeface
      valueFormatter = IValueFormatter { value, entry, _, _ ->
        val num: String = when (metric) {
          Metric.AVERAGE -> formatTime(value.toLong() * 86400 / totalSeconds)
          Metric.PERCENTAGE -> "${value.toLong() * 100 / totalSeconds}%"
          Metric.TOTAL -> formatTime(value.toLong())
        }
        "${(entry as PieEntry).label} $num"
      }
      yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
    }

    with(chart) {
      centerText = "Total\n${formatTime(totalSeconds)}"
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
