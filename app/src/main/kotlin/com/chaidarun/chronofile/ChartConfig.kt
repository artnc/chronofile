// © Art Chaidarun

package com.chaidarun.chronofile

enum class Metric {
  AVERAGE,
  TOTAL,
}

enum class CountMetric {
  OCCURRENCES,
  UNIQUE_DAYS,
}

data class ChartConfig(
  val countMetric: CountMetric = CountMetric.UNIQUE_DAYS,
  val grouped: Boolean = true,
  val stacked: Boolean = true,
  val metric: Metric = Metric.AVERAGE,
  val startTime: Long? = null,
  val endTime: Long? = null,
)
