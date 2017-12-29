package com.chaidarun.chronofile

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.File

class Config(
  /**
   * Map of arbitrary categories to lists of activities and/or other categories.
   *
   * This is used to create buckets of activities to show in graphs.
   *
   * A category name may be an activity name, in which case that activity will be considered a
   * member of the category.
   */
  @Expose
  @SerializedName("categories")
  private val categories: Map<String, List<String>>? = null,
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
    private val mFile = File("/storage/emulated/0/Sync/chronofile.json")

    fun loadConfigFromDisk(): Config {
      val config = if (mFile.exists()) {
        gson.fromJson(mFile.readText(), Config::class.java)
      } else {
        Config()
      }
      config.saveConfigToDisk()
      return config
    }
  }

  /** TODO: Support nested groups */
  private val activityGroups by lazy {
    val activitiesToTopLevelGroups = mutableMapOf<String, String>()
    if (categories != null) {
      val groupsToActivities = categories.toMutableMap()
      while (!groupsToActivities.isEmpty()) {
        val groupName = groupsToActivities.keys.take(1)[0]
        val groupMembers = groupsToActivities.remove(groupName)!!
        groupMembers.forEach {
          activitiesToTopLevelGroups[it] = groupName
        }
      }
    }
    activitiesToTopLevelGroups
  }

  fun getActivityGroup(activity: String) = activityGroups.getOrDefault(activity, activity)

  fun saveConfigToDisk() {
    val textToWrite = gson.toJson(this)
    if (mFile.exists() && mFile.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      mFile.writeText(textToWrite)
    }
  }
}
