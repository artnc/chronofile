package com.chaidarun.chronofile

data class Entry(
  /** The Unix timestamp in seconds of when this activity began */
  val startTime: Long,
  /** Life activity, e.g. "Sleep" */
  val activity: String,
  /** 2-tuple of [latitude, longitude] */
  val latLong: List<Double>? = null,
  /** Free-form metadata */
  val note: String? = null
) {
  fun toTsvRow() = "${activity}\t${latLong?.get(0) ?: ""}\t${latLong?.get(1) ?: ""}\t${note ?: ""}\t${startTime}\n"
}
