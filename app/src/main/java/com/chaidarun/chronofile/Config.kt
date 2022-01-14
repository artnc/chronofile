package com.chaidarun.chronofile

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
) {
  private val activityGroups by lazy {
    mutableMapOf<String, String>().apply {
      unnormalizedActivityGroups?.entries?.forEach { (groupName, groupMembers) ->
        groupMembers.forEach { this[it] = groupName }
      }
    }
  }

  fun getActivityGroup(activity: String) = activityGroups.getOrDefault(activity, activity)

  fun serialize(): String = gson.toJson(this)

  private fun save() = IOUtil.writeFile(file, serialize())

  companion object {
    private val gson by lazy {
      GsonBuilder()
        .disableHtmlEscaping()
        .excludeFieldsWithoutExposeAnnotation()
        .setPrettyPrinting()
        .create()
    }
    private val file = File("${IOUtil.dir}/Sync/Mobile/chronofile.json")

    private fun deserialize(text: String) = gson.fromJson(text, Config::class.java)

    fun fromText(text: String): Config = deserialize(text).apply { save() }

    // TODO use storage access framework
    fun fromFile(): Config =
      (if (file.exists()) deserialize(file.readText()) else Config()).apply { save() }
  }
}
