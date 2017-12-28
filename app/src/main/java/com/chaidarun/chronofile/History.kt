package com.chaidarun.chronofile

import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File

class History {

  companion object {
    private val TAG = "History"
  }

  val entries = mutableListOf<Entry>()
  private val gson by lazy { GsonBuilder().disableHtmlEscaping().create() }
  var currentActivityStartTime = getEpochSeconds()
    private set
  private val mFile = File("/storage/emulated/0/Sync/chronofile.jsonl")

  init {
    loadHistoryFromFile()
  }

  fun addEntry(activity: String) {
    entries += Entry(currentActivityStartTime, activity)
    currentActivityStartTime = getEpochSeconds()
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

  private fun loadHistoryFromFile() {
    currentActivityStartTime = getEpochSeconds()
    if (!mFile.exists()) {
      mFile.writeText(gson.toJson(PlaceholderEntry(currentActivityStartTime)))
    }
    entries.clear()
    mFile.readLines().forEach {
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
    entries.sortBy { it.startTime }
    var lastSeenActivity: String? = null
    entries.removeAll {
      val shouldRemove = it.activity == lastSeenActivity
      lastSeenActivity = it.activity
      shouldRemove
    }

    // Save
    val lines = mutableListOf<String>()
    entries.forEach { lines += gson.toJson(it) }
    lines += gson.toJson(PlaceholderEntry(currentActivityStartTime))
    val textToWrite = lines.joinToString("") { "$it\n" }
    if (mFile.readText() == textToWrite) {
      Log.d(TAG, "File unchanged; skipping write")
    } else {
      mFile.writeText(textToWrite)
    }
  }

  private fun getEpochSeconds() = System.currentTimeMillis() / 1000
}
