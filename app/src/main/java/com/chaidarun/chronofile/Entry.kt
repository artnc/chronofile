package com.chaidarun.chronofile

import com.google.gson.annotations.SerializedName

data class Entry(
  @SerializedName("t") val startTime: Long,
  @SerializedName("a") val activity: String,
  @SerializedName("l") val latLong: List<Double>? = null,
  @SerializedName("m") val metadata: String? = null
)
