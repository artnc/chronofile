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
  val locations: Map<String, List<Double>>? = null
) {
  private val activityGroups by lazy {
    mutableMapOf<String, String>().apply {
      unnormalizedActivityGroups?.entries?.forEach { (groupName, groupMembers) ->
        groupMembers.forEach { this[it] = groupName }
      }
    }
  }

  fun getActivityGroup(activity: String) = activityGroups.getOrDefault(activity, activity)

  fun serialize() = gson.toJson(this)

  fun saveFile() {
    val textToWrite = serialize()
    if (file.exists() && file.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      file.writeText(textToWrite)
    }
  }

  companion object {
    /** 0.0005 degrees latitude is roughly 182 ft */
    val LOCATION_SNAP_RADIUS_SQUARED = Math.pow(0.0005, 2.0)
    private val gson by lazy {
      GsonBuilder()
        .disableHtmlEscaping()
        .excludeFieldsWithoutExposeAnnotation()
        .setPrettyPrinting()
        .create()
    }
    private val file = File("/storage/emulated/0/Sync/chronofile.json")

    private fun deserialize(text: String) = gson.fromJson(text, Config::class.java)

    fun fromText(text: String): Config = deserialize(text).apply { saveFile() }

    fun fromFile(): Config {
      val config = if (file.exists()) deserialize(file.readText()) else Config()
      config.saveFile()
      return config
    }
  }
}
