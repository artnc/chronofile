package com.chaidarun.chronofile

import android.graphics.Color

abstract class GraphFragment : BaseFragment() {

  /**
   * Determines the date range that should be used to render the pie chart.
   *
   * This takes into account the earliest recorded entry, the last recorded entry, the
   * user-selected start date, the user-selected end date, and the graph metric.
   */
  protected fun getChartRange(history: History, graphSettings: GraphSettings): Pair<Long, Long> {
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
  }
}
