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
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_pie.*
import org.jetbrains.anko.toast
import java.util.*

enum class Metric { AVERAGE, PERCENTAGE, TOTAL }
data class GraphSettings(
  val grouped: Boolean = true,
  val metric: Metric = Metric.AVERAGE,
  val startTime: Long? = null,
  val endTime: Long? = null
)

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
      setExtraOffsets(41f, 41f, 41f, 41f)
      setHoleColor(Color.TRANSPARENT)
      setTransparentCircleAlpha(0)
    }

    // Set date picker range to history range by default
    Store.dispatch(Action.SetGraphRangeStart(Store.state.value.history!!.entries[0].startTime))
    Store.dispatch(Action.SetGraphRangeEnd(Store.state.value.history!!.currentActivityStartTime))

    var startTime: Long? = null
    var endTime: Long? = null
    disposables = CompositeDisposable().apply {
      add(Store.state
        .filter { it.config != null && it.history != null }
        .map { Triple(it.config!!, it.history!!, it.graphSettings) }
        .distinctUntilChanged()
        .subscribe { update(it) }
      )
      add(Store.state
        .map { it.graphSettings.startTime }
        .distinctUntilChanged()
        .subscribe {
          startTime = it
          if (it != null) startDate.text = DATE_FORMAT.format(Date(it * 1000))
        }
      )
      add(Store.state
        .map { it.graphSettings.endTime }
        .distinctUntilChanged()
        .subscribe {
          endTime = it
          if (it != null) endDate.text = DATE_FORMAT.format(Date(it * 1000))
        }
      )
      add(RxView.clicks(startDate).subscribe {
        val fragment = DatePickerFragment().apply {
          arguments = Bundle().apply {
            putString(DatePickerFragment.ENDPOINT, "start")
            putLong(DatePickerFragment.TIMESTAMP, startTime ?: epochSeconds())
          }
        }
        fragment.show(fragmentManager, "datePicker")
      })
      add(RxView.clicks(endDate).subscribe {
        val fragment = DatePickerFragment().apply {
          arguments = Bundle().apply {
            putString(DatePickerFragment.ENDPOINT, "end")
            putLong(DatePickerFragment.TIMESTAMP, endTime ?: epochSeconds())
          }
        }
        fragment.show(fragmentManager, "datePicker")
      })
    }
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

  /**
   * Determines the date range that should be used to render the pie chart.
   *
   * This takes into account the earliest recorded entry, the last recorded entry, the
   * user-selected start date, the user-selected end date, and the graph metric.
   */
  private fun getChartRange(history: History, graphSettings: GraphSettings): Pair<Long, Long> {
    val historyStart = history.entries[0].startTime
    val historyEnd = history.currentActivityStartTime
    val pickerStart = graphSettings.startTime ?: 0
    val pickerEnd = if (graphSettings.endTime == null) {
      Long.MAX_VALUE
    } else {
      // Must append a day's worth of seconds to the range to make it inclusive
      graphSettings.endTime + DAY_SECONDS
    }
    val rangeEnd = Math.min(historyEnd, pickerEnd)
    var rangeStart = Math.max(historyStart, pickerStart)
    if (graphSettings.metric == Metric.AVERAGE) {
      rangeStart = rangeEnd - ((rangeEnd - rangeStart) / DAY_SECONDS * DAY_SECONDS)
    }
    return Pair(rangeStart, rangeEnd)
  }

  /** (Re-)renders pie chart */
  private fun update(state: Triple<Config, History, GraphSettings>) {
    val (config, history, graphSettings) = state
    Log.d(TAG, "Rendering pie chart")

    // Determine date range
    val (rangeStart, rangeEnd) = getChartRange(history, graphSettings)
    val rangeSeconds = rangeEnd - rangeStart
    if (rangeSeconds <= 0) {
      toast("No data to show!")
      return
    }

    // Get data
    val (grouped, metric) = graphSettings
    val sliceMap = mutableMapOf<String, Long>()
    with(history) {
      // Bucket entries into slices
      var endTime = rangeEnd
      for (entry in entries.reversed()) {
        // Skip entries from after date range
        if (entry.startTime >= rangeEnd) {
          continue
        }

        // Process entry
        val startTime = Math.max(entry.startTime, rangeStart)
        val seconds = endTime - startTime
        val slice = if (grouped) config.getActivityGroup(entry.activity) else entry.activity
        sliceMap[slice] = sliceMap.getOrDefault(slice, 0) + seconds
        endTime = startTime

        // Skip entries from before date range
        if (startTime <= rangeStart) {
          break
        }
      }
    }

    // Sort slices by size, consolidating small slices into "Other"
    val sliceThresholdSeconds = rangeSeconds * MIN_SLICE_PERCENT
    var bigSliceSeconds = 0L
    val sliceList = sliceMap.entries
      .sortedByDescending { it.value }
      .takeWhile {
        val shouldTake = it.value > sliceThresholdSeconds
        if (shouldTake) bigSliceSeconds += it.value
        shouldTake
      }
      .map { Pair(it.key, it.value) }
      .toMutableList()
    if (bigSliceSeconds < rangeSeconds) sliceList += Pair("Other", rangeSeconds - bigSliceSeconds)

    // Show data
    val pieEntries = sliceList.map { (key, value) -> PieEntry(value.toFloat(), key) }
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
      valueLinePart1Length = 0.5f
      valueLinePart2Length = 0f
      valueTextColor = Color.WHITE
      valueTextSize = 12f
      valueTypeface = App.instance.typeface
      valueFormatter = IValueFormatter { value, entry, _, _ ->
        val num: String = when (metric) {
          Metric.AVERAGE -> formatTime(value.toLong() * DAY_SECONDS / rangeSeconds)
          Metric.PERCENTAGE -> "${value.toLong() * 100 / rangeSeconds}%"
          Metric.TOTAL -> formatTime(value.toLong())
        }
        "${(entry as PieEntry).label} $num"
      }
      yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
    }

    with(chart) {
      centerText = "Total\n${formatTime(rangeSeconds)}"
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

  companion object {
    /** Slices smaller than this will get bucketed into "Other" */
    private val MIN_SLICE_PERCENT = 0.015
  }
}
