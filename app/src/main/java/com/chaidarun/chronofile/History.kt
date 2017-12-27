package com.chaidarun.chronofile

import java.io.File

class History {

  val entries = mutableListOf<Entry>()
  var currentActivityStartTime = getEpochSeconds()
    private set
  private val mFile = File("/storage/emulated/0/Sync/chronofile.csv")

  init {
    loadHistoryFromFile()
  }

  fun addEntry(activity: String) {
    entries += Entry(currentActivityStartTime, activity)
    normalizeEntries()
    currentActivityStartTime = getEpochSeconds()
    saveHistoryToDisk()
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
      mFile.writeText("$currentActivityStartTime")
    }
    entries.clear()
    mFile.readLines().forEach {
      val pieces = it.split(',')
      val startTime = pieces[0].toLong()
      when (pieces.size) {
        1 -> currentActivityStartTime = startTime
        else -> entries += Entry(startTime, pieces[1])
      }
    }
    normalizeEntries()
    saveHistoryToDisk()
  }

  private fun saveHistoryToDisk() {
    val lines = mutableListOf<String>()
    entries.forEach { lines += "${it.startTime},${it.activity}" }
    lines += currentActivityStartTime.toString()
    mFile.writeText(lines.joinToString("\n"))
  }

  private fun normalizeEntries() {
    entries.sortBy { it.startTime }
    var lastSeenActivity: String? = null
    entries.removeAll {
      val shouldRemove = it.activity == lastSeenActivity
      lastSeenActivity = it.activity
      shouldRemove
    }
  }

  private fun getEpochSeconds() = System.currentTimeMillis() / 1000
}
