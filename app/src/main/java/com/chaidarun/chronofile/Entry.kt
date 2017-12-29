package com.chaidarun.chronofile

import android.util.Log
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Entry(
  /** The Unix timestamp in seconds of when this activity began */
  @Expose
  @SerializedName("t")
  val startTime: Long,
  /** Life activity, e.g. "Sleep" */
  @Expose
  @SerializedName("a")
  val activity: String,
  /** 2-tuple of [latitude, longitude] */
  @Expose
  @SerializedName("l")
  val latLong: List<Double>? = null,
  /** Free-form metadata */
  @Expose
  @SerializedName("n")
  val note: String? = null
) {
  fun snapToKnownLocation(): Entry {
    latLong ?: return this
    val snapLocations = App.instance.config.locations ?: return this

    // Snap to known location if in range
    val (x1, y1) = latLong
    for (snapLocation in snapLocations) {
      val (x2, y2) = snapLocation
      val distanceSquared = Math.pow(x2 - x1, 2.0) + Math.pow(y2 - y1, 2.0)
      if (distanceSquared == 0.0) {
        return this
      }
      if (distanceSquared < Config.LOCATION_SNAP_RADIUS_SQUARED) {
        Log.d(TAG, "Snapping ($x1. $y1) to ($x2. $y2)")
        return Entry(startTime, activity, snapLocation, note)
      }
    }
    return this
  }

  companion object {
    private val TAG = "Entry"
  }
}
