package com.chaidarun.chronofile

import com.google.gson.annotations.SerializedName

data class Entry(
  /** The Unix timestamp in seconds of when this activity began */
  @SerializedName("t") val startTime: Long,
  /** Life activity, e.g. "Sleep" */
  @SerializedName("a") val activity: String,
  /** 2-tuple of [latitude, longitude] */
  @SerializedName("l") val latLong: List<Double>? = null,
  /** Free-form metadata */
  @SerializedName("n") val note: String? = null
)
