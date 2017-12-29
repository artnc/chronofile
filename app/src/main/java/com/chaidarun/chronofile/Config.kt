package com.chaidarun.chronofile

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.File

class Config(
  /**
   * Map of arbitrary group names to (mutually exclusive) lists of activities.
   *
   * This is used to create buckets of activities to show in graphs.
   */
  @Expose
  @SerializedName("activityGroups")
  private val unnormalizedActivityGroups: Map<String, List<String>>? = null,
  /**
   * Known coordinates to which nearby locations should snap.
   *
   * This is used to ensure that all activities occurring in the same physical location always get
   * assigned the same coordinates.
   */
  @Expose
  @SerializedName("locations")
  val locations: List<List<Double>>? = null
) {
  companion object {
    /** 0.0005 degrees latitude is roughly 182 ft */
    val LOCATION_SNAP_RADIUS_SQUARED = Math.pow(0.0005, 2.0)
    private val TAG = "Config"
    private val gson by lazy {
      GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create()
    }
    private val file = File("/storage/emulated/0/Sync/chronofile.json")

    fun loadConfigFromDisk(): Config {
      val config = if (file.exists()) {
        gson.fromJson(file.readText(), Config::class.java)
      } else {
        Config()
      }
      config.saveConfigToDisk()
      return config
    }
  }

  private val activityGroups by lazy {
    mutableMapOf<String, String>().apply {
      unnormalizedActivityGroups?.entries?.forEach { (groupName, groupMembers) ->
        groupMembers.forEach { this[it] = groupName }
      }
    }
  }

  fun getActivityGroup(activity: String) = activityGroups.getOrDefault(activity, activity)

  fun saveConfigToDisk() {
    val textToWrite = gson.toJson(this)
    if (file.exists() && file.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      file.writeText(textToWrite)
    }
  }
}
