package com.chaidarun.chronofile

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class PlaceholderEntry(@Expose @SerializedName("t") val startTime: Long)
