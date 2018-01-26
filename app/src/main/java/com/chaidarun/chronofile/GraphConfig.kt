package com.chaidarun.chronofile

enum class Metric { AVERAGE, PERCENTAGE, TOTAL }
data class GraphConfig(
  val grouped: Boolean = true,
  val stacked: Boolean = true,
  val includeSleep: Boolean = true,
  val metric: Metric = Metric.AVERAGE,
  val startTime: Long? = null,
  val endTime: Long? = null
)
