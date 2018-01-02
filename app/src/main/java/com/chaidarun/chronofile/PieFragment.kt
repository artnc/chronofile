package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_pie.*
import org.jetbrains.anko.toast

class PieFragment : GraphFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_pie, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

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

    // Populate form with current state
    with(Store.state.value) {
      (when (graphSettings.grouped) {
        true -> radioGrouped
        false -> radioIndividual
      }).isChecked = true
      (when (graphSettings.metric) {
        Metric.AVERAGE -> radioAverage
        Metric.PERCENTAGE -> radioPercentage
        Metric.TOTAL -> radioTotal
      }).isChecked = true
    }

    disposables = CompositeDisposable().apply {
      add(Store.state
        .filter { it.config != null && it.history != null }
        .map { Triple(it.config!!, it.history!!, it.graphSettings) }
        .distinctUntilChanged()
        .subscribe { update(it) }
      )
    }
  }

  /** (Re-)renders pie chart */
  private fun update(state: Triple<Config, History, GraphSettings>) {
    val (config, history, graphSettings) = state
    val start = System.currentTimeMillis()

    // Determine date range
    val (rangeStart, rangeEnd) = getChartRange(history, graphSettings)
    val rangeSeconds = rangeEnd - rangeStart
    if (rangeSeconds <= 0) {
      activity.toast("No data to show!")
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
      colors = COLORS
      valueLineColor = Color.TRANSPARENT
      valueLinePart1Length = 0.5f
      valueLinePart2Length = 0f
      valueTextColor = Color.WHITE
      valueTextSize = 12f
      valueTypeface = App.instance.typeface
      valueFormatter = IValueFormatter { value, entry, _, _ ->
        val num: String = when (metric) {
          Metric.AVERAGE -> formatDuration(value.toLong() * DAY_SECONDS / rangeSeconds)
          Metric.PERCENTAGE -> "${value.toLong() * 100 / rangeSeconds}%"
          Metric.TOTAL -> formatDuration(value.toLong())
        }
        "${(entry as PieEntry).label} $num"
      }
      yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
    }

    with(chart) {
      centerText = "Total\n${formatDuration(rangeSeconds)}"
      data = PieData(pieDataSet)
      invalidate()
    }

    Log.d(TAG, "Rendered pie chart in ${System.currentTimeMillis() - start} ms")
  }

  companion object {
    /** Slices smaller than this will get bucketed into "Other" */
    private val MIN_SLICE_PERCENT = 0.015
  }
}
