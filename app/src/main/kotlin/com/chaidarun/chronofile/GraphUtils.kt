// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Color
import androidx.core.graphics.toColorInt
import kotlin.math.sqrt

/** D3.js schemeTableau10 and schemeDark2 */
val CHART_COLORS by lazy {
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
    .map { it.toColorInt() }
}

const val CHART_LABEL_COLOR = Color.WHITE
const val CHART_LABEL_FONT_SIZE = 12f
const val OTHER_SLICE_NAME = "Other"

/** Slices smaller than this will get bucketed into "Other" */
private const val MIN_SLICE_PERCENT = 0.015

enum class Aggregation {
  DAY,
  DAY_OF_WEEK,
  TOTAL,
}

/**
 * Determines the date range that should be used to render each chart.
 *
 * This takes into account the earliest recorded entry, the last recorded entry, the user-selected
 * start date, the user-selected end date, and the graph metric.
 */
fun getChartRange(history: History, graphConfig: GraphConfig): Pair<Long, Long> {
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
  val rangeEnd = minOf(historyEnd, pickerEnd)
  var rangeStart = maxOf(historyStart, pickerStart)
  if (graphConfig.metric == Metric.AVERAGE) {
    rangeStart = rangeEnd - (rangeEnd - rangeStart) / DAY_SECONDS * DAY_SECONDS
  }
  return Pair(rangeStart, rangeEnd)
}

/** Returns a ({ bucket: { slice: duration } }, [(slice, total duration)]) */
fun aggregateEntries(
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
    val startTime = maxOf(entry.startTime, rangeStart)
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
            Aggregation.TOTAL -> error("unreachable")
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
  sliceList.add(OTHER_SLICE_NAME to totalDuration - sliceList.map { it.second }.sum())
  val nonOtherSlices = sliceList.map { it.first }.toSet()
  val bucketsWithOther = buckets.mapValues { (_, value) ->
    val (matching, other) = value.entries.partition { it.key in nonOtherSlices }
    matching.associate { it.key to it.value } + (OTHER_SLICE_NAME to other.sumOf { it.value })
  }

  return Pair(bucketsWithOther, sliceList)
}

/**
 * Counts each activity (or group, when [GraphConfig.grouped]) over entries starting within
 * [rangeStart, rangeEnd), returned as (slice, count) pairs sorted by descending count. Per [GraphConfig.countMetric]
 * the count is either the raw number of recordings ([CountMetric.OCCURRENCES]) or the number of
 * distinct local days on which it was recorded ([CountMetric.UNIQUE_DAYS]). Either way an entry is
 * attributed to the single day it started on.
 */
fun activityCounts(
  config: Config,
  history: History,
  graphConfig: GraphConfig,
  rangeStart: Long,
  rangeEnd: Long,
): List<Pair<String, Int>> {
  val uniqueDays = graphConfig.countMetric == CountMetric.UNIQUE_DAYS
  // Per slice, collect a set of distinct keys then count them: for UNIQUE_DAYS the key is the
  // entry's start-day midnight (so same-day recordings collapse to one), and for OCCURRENCES it's
  // the entry's own start time, which is unique per entry so every recording counts once
  val keySets = mutableMapOf<String, MutableSet<Long>>()
  for (entry in history.entries) {
    if (entry.startTime < rangeStart || entry.startTime >= rangeEnd) continue
    val slice = if (graphConfig.grouped) config.getActivityGroup(entry.activity) else entry.activity
    val key = if (uniqueDays) getPreviousMidnight(entry.startTime) else entry.startTime
    keySets.getOrPut(slice) { mutableSetOf() }.add(key)
  }
  return keySets.mapValues { it.value.size }.toList().sortedByDescending { it.second }
}

/**
 * Result of [buildCorrelationMatrix]: parallel [labels] alongside an NxN [values] grid where cell
 * [i][j] holds the Pearson correlation of daily durations between activities i and j (NaN when
 * undefined, i.e. either activity has no day-to-day variance)
 */
class CorrelationMatrix(val labels: List<String>, val values: Array<DoubleArray>)

/** Pearson correlation coefficient of two equal-length series, or NaN if either has no variance */
fun pearson(x: List<Double>, y: List<Double>): Double {
  val meanX = x.average()
  val meanY = y.average()
  var sumXY = 0.0
  var sumXX = 0.0
  var sumYY = 0.0
  for (i in x.indices) {
    val dx = x[i] - meanX
    val dy = y[i] - meanY
    sumXY += dx * dy
    sumXX += dx * dx
    sumYY += dy * dy
  }
  // Zero variance on either axis leaves correlation undefined (e.g. an activity never recorded)
  val denominator = sqrt(sumXX * sumYY)
  return if (denominator == 0.0) Double.NaN else sumXY / denominator
}

/**
 * Builds a Pearson-correlation matrix of daily activity durations over
 * [rangeStart, rangeEnd), reusing [aggregateEntries]'s per-day buckets and filtered slice list
 * (dropping the "Other" bucket). Each recorded day is one observation; an activity not done that
 * day counts as 0 seconds.
 */
fun buildCorrelationMatrix(
  config: Config,
  history: History,
  graphConfig: GraphConfig,
  rangeStart: Long,
  rangeEnd: Long,
): CorrelationMatrix {
  val (buckets, sliceList) =
    aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.DAY)
  val labels = sliceList.map { it.first }.filter { it != OTHER_SLICE_NAME }
  val days = buckets.keys.sorted()
  val series = labels.map { label -> days.map { (buckets[it]?.get(label) ?: 0L).toDouble() } }
  val values =
    Array(labels.size) { i ->
      DoubleArray(labels.size) { j -> if (i == j) 1.0 else pearson(series[i], series[j]) }
    }
  return CorrelationMatrix(labels, values)
}
