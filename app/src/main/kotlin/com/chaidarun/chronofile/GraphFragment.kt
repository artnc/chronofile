// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Color

abstract class GraphFragment : BaseFragment() {

  /**
   * Determines the date range that should be used to render each chart.
   *
   * This takes into account the earliest recorded entry, the last recorded entry, the user-selected
   * start date, the user-selected end date, and the graph metric.
   */
  protected fun getChartRange(history: History, graphConfig: GraphConfig): Pair<Long, Long> {
    val historyEnd = history.currentActivityStartTime
    val historyStart = history.entries.getOrNull(0)?.startTime ?: historyEnd
    val pickerStart = graphConfig.startTime ?: 0
    val pickerEnd =
      if (graphConfig.endTime == null) {
        Long.MAX_VALUE
      } else {
        // Must append a day's worth of seconds to the range to make it inclusive
        graphConfig.endTime + DAY_SECONDS
      }
    val rangeEnd = Math.min(historyEnd, pickerEnd)
    var rangeStart = Math.max(historyStart, pickerStart)
    if (graphConfig.metric == Metric.AVERAGE) {
      rangeStart = rangeEnd - (rangeEnd - rangeStart) / DAY_SECONDS * DAY_SECONDS
    }
    return Pair(rangeStart, rangeEnd)
  }

  protected enum class Aggregation {
    DAY,
    DAY_OF_WEEK,
    TOTAL,
  }

  /** Returns a ({ bucket: { slice: duration } }, { slice: total duration }) */
  protected fun aggregateEntries(
    config: Config,
    history: History,
    graphConfig: GraphConfig,
    rangeStart: Long,
    rangeEnd: Long,
    aggregation: Aggregation,
  ): Pair<Map<Long, Map<String, Long>>, List<Pair<String, Long>>> {
    val grouped = graphConfig.grouped
    var endTime = rangeEnd
    val buckets = mutableMapOf<Long, MutableMap<String, Long>>()
    val sliceMap = mutableMapOf<String, Long>()
    for (entry in history.entries.reversed()) {
      // Skip entries from after date range
      if (entry.startTime >= rangeEnd) {
        continue
      }

      // Get slice(s) depending on whether entry crosses midnight
      val startTime = Math.max(entry.startTime, rangeStart)
      val bucketIncrements: Map<Long, Long> =
        when (aggregation) {
          Aggregation.DAY,
          Aggregation.DAY_OF_WEEK -> {
            val midnightBeforeStart = getPreviousMidnight(startTime)
            val midnightBeforeEnd = getPreviousMidnight(endTime)
            val pieces =
              if (midnightBeforeStart != midnightBeforeEnd) {
                mapOf(
                  midnightBeforeEnd to (endTime - midnightBeforeEnd),
                  midnightBeforeStart to (midnightBeforeEnd - startTime),
                )
              } else {
                mapOf(midnightBeforeStart to (endTime - startTime))
              }

            when (aggregation) {
              Aggregation.DAY -> pieces
              Aggregation.DAY_OF_WEEK -> pieces.mapKeys { getDayOfWeek(it.key).value.toLong() }
              else -> error("Unhandled aggregation")
            }
          }
          Aggregation.TOTAL -> mapOf(0L to (endTime - startTime))
        }

      // Record slices
      val slice = if (grouped) config.getActivityGroup(entry.activity) else entry.activity
      bucketIncrements.forEach { (bucket, increment) ->
        val bucketGractivities = buckets.getOrPut(bucket) { mutableMapOf() }
        bucketGractivities[slice] = bucketGractivities.getOrDefault(slice, 0) + increment
        sliceMap[slice] = sliceMap.getOrDefault(slice, 0) + increment
      }
      endTime = startTime

      // Skip entries from before date range
      if (entry.startTime <= rangeStart) {
        break
      }
    }

    // Consolidate small slices into "Other" slice
    val totalDuration = sliceMap.values.sum()
    val sliceList =
      sliceMap
        .toList()
        .sortedByDescending { it.second }
        .takeWhile { it.second >= totalDuration * MIN_SLICE_PERCENT }
        .toMutableList()
    sliceList.add(OTHER_SLICE_NAME to sliceMap.values.sum() - sliceList.map { it.second }.sum())
    val nonOtherSlices = sliceList.map { it.first }.toSet()
    val bucketsWithOther =
      buckets.mapValues { (_, value) ->
        val newMap = value.filter { it.key in nonOtherSlices }.toMutableMap()
        newMap[OTHER_SLICE_NAME] = value.filter { it.key !in nonOtherSlices }.map { it.value }.sum()
        newMap
      }

    return Pair(bucketsWithOther, sliceList)
  }

  companion object {
    /** D3.js schemeTableau10 and schemeDark2 */
    val COLORS by lazy {
      listOf(
          "#4e79a7",
          "#f28e2c",
          "#e15759",
          "#76b7b2",
          "#59a14f",
          "#edc949",
          "#af7aa1",
          "#ff9da7",
          "#9c755f",
          "#bab0ab",
          "#1b9e77",
          "#d95f02",
          "#7570b3",
          "#e7298a",
          "#66a61e",
          "#e6ab02",
          "#a6761d",
          "#666666",
        )
        .map { Color.parseColor(it) }
    }

    /** Slices smaller than this will get bucketed into "Other" */
    private const val MIN_SLICE_PERCENT = 0.015

    /** TODO: Make this `protected` once Kotlin supports that */
    const val LABEL_COLOR = Color.WHITE
    /** TODO: Make this `protected` once Kotlin supports that */
    const val LABEL_FONT_SIZE = 12f
    /** TODO: Make this `protected` once Kotlin supports that */
    const val OTHER_SLICE_NAME = "Other"
  }
}
