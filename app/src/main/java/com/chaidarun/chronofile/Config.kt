package com.chaidarun.chronofile

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Config(
  /**
   * Map of arbitrary group names to (mutually exclusive) lists of activities.
   *
   * This is used to create buckets of activities to show in graphs.
   */
  @Expose
  @SerializedName("activityGroups")
  private val unnormalizedActivityGroups: Map<String, List<String>>? = null,
  /** Map of NFC tag IDs to [activity, note] pairs as registered by the user. */
  @Expose @SerializedName("nfc") val nfcTags: Map<String, List<String>>? = null,
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

  fun save() = IOUtil.writeFile(FILENAME, serialize())

  companion object {
    private val gson by lazy {
      GsonBuilder()
        .disableHtmlEscaping()
        .excludeFieldsWithoutExposeAnnotation()
        .setPrettyPrinting()
        .create()
    }
    private const val FILENAME = "chronofile.json"

    private fun deserialize(text: String) = gson.fromJson(text, Config::class.java)

    fun fromText(text: String): Config = deserialize(text).apply { save() }

    fun fromFile(): Config {
      val text = IOUtil.readFile(FILENAME)
      return if (text == null) Config().apply { save() } else deserialize(text)
    }
  }
}
