package com.chaidarun.chronofile

import android.graphics.Color

abstract class GraphFragment : BaseFragment() {

  /**
   * Determines the date range that should be used to render the pie chart.
   *
   * This takes into account the earliest recorded entry, the last recorded entry, the
   * user-selected start date, the user-selected end date, and the graph metric.
   */
  protected fun getChartRange(history: History, graphConfig: GraphConfig): Pair<Long, Long> {
    val historyStart = history.entries[0].startTime
    val historyEnd = history.currentActivityStartTime
    val pickerStart = graphConfig.startTime ?: 0
    val pickerEnd = if (graphConfig.endTime == null) {
      Long.MAX_VALUE
    } else {
      // Must append a day's worth of seconds to the range to make it inclusive
      graphConfig.endTime + DAY_SECONDS
    }
    val rangeEnd = Math.min(historyEnd, pickerEnd)
    var rangeStart = Math.max(historyStart, pickerStart)
    if (graphConfig.metric == Metric.AVERAGE) {
      rangeStart = rangeEnd - ((rangeEnd - rangeStart) / DAY_SECONDS * DAY_SECONDS)
    }
    return Pair(rangeStart, rangeEnd)
  }

  protected fun getSliceList(
    config: Config,
    history: History,
    graphConfig: GraphConfig,
    rangeStart: Long,
    rangeEnd: Long
  ): MutableList<Pair<String, Long>> {
    // Get data
    val grouped = graphConfig.grouped
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
    val rangeSeconds = rangeEnd - rangeStart
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

    return sliceList
  }

  companion object {
    val COLORS by lazy {
      listOf(
        "#66BB6A",
        "#388E3C",
        "#81C784",
        "#4CAF50",
        "#2E7D32",
        "#1B5E20",
        "#A5D6A7",
        "#43A047"
      ).map { Color.parseColor(it) }
    }

    /** Slices smaller than this will get bucketed into "Other" */
    private val MIN_SLICE_PERCENT = 0.015
  }
}
