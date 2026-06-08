// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Config(
  /**
   * Map of arbitrary group names to (mutually exclusive) lists of activities.
   *
   * This is used to create buckets of activities to show in graphs.
   */
  @SerialName("activityGroups")
  private val unnormalizedActivityGroups: Map<String, List<String>>? = null,
  /** Map of NFC tag IDs to [activity, note] pairs as registered by the user. */
  @SerialName("nfc") val nfcTags: Map<String, List<String>>? = null,
) {
  // Delegated (lazy) properties aren't serialized, so this stays out of the JSON
  private val activityGroups by lazy {
    unnormalizedActivityGroups
      .orEmpty()
      .flatMap { (groupName, groupMembers) -> groupMembers.map { it to groupName } }
      .toMap()
  }

  fun getActivityGroup(activity: String) = activityGroups[activity] ?: activity

  fun serialize(): String = json.encodeToString(serializer(), this)

  fun save() = IOUtil.writeFile(FILENAME, serialize())

  companion object {
    private val json = Json {
      // Tolerate hand-edited configs: extra keys, and lenient JSON (trailing commas etc.) that the
      // old Gson reader accepted, so a hand-edited file can't hard-crash startup
      ignoreUnknownKeys = true
      isLenient = true
      prettyPrint = true
      prettyPrintIndent = "  "
    }
    private const val FILENAME = "chronofile.json"

    private fun deserialize(text: String) = json.decodeFromString(serializer(), text)

    fun fromText(text: String): Config = deserialize(text).apply { save() }

    fun fromFile(): Config {
      val text = IOUtil.readFile(FILENAME) ?: return Config().apply { save() }
      return try {
        deserialize(text)
      } catch (e: Exception) {
        // Don't save() over the unparseable file; keep it so the user can fix it by hand
        Log.w(TAG, "Failed to parse config; falling back to empty config", e)
        App.toast("Failed to load config")
        Config()
      }
    }
  }
}
