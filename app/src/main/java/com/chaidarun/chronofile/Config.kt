package com.chaidarun.chronofile

import android.util.Log
import com.google.gson.GsonBuilder
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
  @SerializedName("categories") val categories: Map<String, List<String>>? = null,
  /**
   * Known coordinates to which nearby locations should snap.
   *
   * This is used to ensure that all activities occurring in the same physical location always get
   * assigned the same coordinates.
   */
  @SerializedName("locations") val locations: List<List<Double>>? = null
) {
  companion object {
    private val TAG = "Config"
    private val gson by lazy { GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create() }
    private val mFile = File("/storage/emulated/0/Sync/chronofile.json")

    fun loadConfigFromDisk(): Config = if (mFile.exists()) {
      gson.fromJson(mFile.readText(), Config::class.java)
    } else {
      Config().apply { saveConfigToDisk() }
    }
  }

  fun saveConfigToDisk() {
    val textToWrite = gson.toJson(this)
    if (mFile.exists() && mFile.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      mFile.writeText(textToWrite)
    }
  }
}
