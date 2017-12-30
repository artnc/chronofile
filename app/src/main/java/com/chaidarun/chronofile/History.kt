package com.chaidarun.chronofile

import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.gson.GsonBuilder
import java.io.File

class History {

  val entries = mutableListOf<Entry>()
  private val gson by lazy {
    GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create()
  }
  var currentActivityStartTime = getEpochSeconds()
    private set
  private val file = File("/storage/emulated/0/Sync/chronofile.jsonl")
  private val locationClient by lazy {
    LocationServices.getFusedLocationProviderClient(App.ctx)
  }

  init {
    loadHistoryFromFile()
    RxBus.listen().ofType(AddEntryAction::class.java).subscribe {
      addEntry(it.activity, it.note, it.callback)
    }
  }

  private fun sanitizeActivityAndNote(
    activity: String,
    note: String?
  ) = Pair(activity.trim(), if (note.isNullOrBlank()) null else note!!.trim())

  private fun addEntry(activity: String, note: String?, callback: (Entry) -> Any) {
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    getLocation {
      val entry = Entry(currentActivityStartTime, sanitizedActivity, it?.toList(), sanitizedNote)
      entries += entry
      currentActivityStartTime = getEpochSeconds()
      normalizeEntriesAndSaveHistoryToDisk()
      callback(entry)
      RxBus.dispatch(AddedEntryAction())
    }
  }

  fun editEntry(oldStartTime: Long, newStartTime: String, activity: String, note: String?) {
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    val entryIndex = entries.indexOfFirst { it.startTime == oldStartTime }
    val oldEntry = entries[entryIndex]
    val newStartTime = try {
      val enteredTime = newStartTime.trim().toLong()
      if (enteredTime > 15e8 && enteredTime <= getEpochSeconds()) enteredTime else oldStartTime
    } catch (e: Exception) {
      oldStartTime
    }
    entries[entryIndex] = Entry(newStartTime, sanitizedActivity, oldEntry.latLong, sanitizedNote)
    normalizeEntriesAndSaveHistoryToDisk()
  }

  fun removeEntries(startTimes: Collection<Long>) {
    entries.removeAll { it.startTime in startTimes }
    normalizeEntriesAndSaveHistoryToDisk()
  }

  fun getFuzzyTimeSinceLastEntry(): String {
    val elapsedSeconds = getEpochSeconds() - currentActivityStartTime
    val elapsedMinutes = elapsedSeconds / 60
    val elapsedHours = elapsedMinutes / 60
    return when {
      elapsedHours > 0 -> "$elapsedHours hours"
      elapsedMinutes > 0 -> "$elapsedMinutes minutes"
      else -> "$elapsedSeconds seconds"
    }
  }

  private fun getLocation(callback: (Pair<Double, Double>?) -> Unit) {
    try {
      locationClient.lastLocation.addOnCompleteListener {
        if (it.isSuccessful && it.result != null) {
          callback(Pair(it.result.latitude, it.result.longitude))
        } else {
          callback(null)
        }
      }
      return
    } catch (e: SecurityException) {
      Log.i(TAG, "Failed to get location")
    }
    callback(null)
  }

  fun loadHistoryFromFile() {
    currentActivityStartTime = getEpochSeconds()
    if (!file.exists()) {
      file.writeText(gson.toJson(PlaceholderEntry(currentActivityStartTime)))
    }
    entries.clear()
    file.readLines().forEach {
      if (',' in it) {
        entries += gson.fromJson(it, Entry::class.java)
      } else if (it.trim().isNotEmpty()) {
        currentActivityStartTime = gson.fromJson(it, PlaceholderEntry::class.java).startTime
      }
    }
    normalizeEntriesAndSaveHistoryToDisk()
  }

  private fun normalizeEntriesAndSaveHistoryToDisk() {
    // Normalize
    entries.replaceAll { it.snapToKnownLocation() }
    entries.sortBy { it.startTime }
    var lastSeenActivityAndNote: Pair<String, String?>? = null
    entries.removeAll {
      val activityAndNote = Pair(it.activity, it.note)
      val shouldRemove = activityAndNote == lastSeenActivityAndNote
      lastSeenActivityAndNote = activityAndNote
      shouldRemove
    }

    // Save
    val lines = mutableListOf<String>()
    entries.forEach { lines += gson.toJson(it) }
    lines += gson.toJson(PlaceholderEntry(currentActivityStartTime))
    val textToWrite = lines.joinToString("") { "$it\n" }
    if (file.exists() && file.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      file.writeText(textToWrite)
    }
  }

  private fun getEpochSeconds() = System.currentTimeMillis() / 1000

  companion object {
    private val TAG = "History"
  }
}
