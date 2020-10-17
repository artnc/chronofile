package com.chaidarun.chronofile

import android.os.AsyncTask
import android.util.Log
import com.google.android.gms.location.LocationServices
import java.io.File

data class History(val entries: List<Entry>, val currentActivityStartTime: Long) {

  fun withEditedEntry(
    oldStartTime: Long,
    editedStartTime: String,
    activity: String,
    note: String?
  ): History {
    // Collect inputs
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    val newStartTime = try {
      val trimmedEditedStartTime = editedStartTime.trim()
      val enteredTime = when {
        trimmedEditedStartTime == "" -> oldStartTime
        ':' in trimmedEditedStartTime -> {
          val now = epochSeconds()
          val (hours, minutes) = trimmedEditedStartTime.split(':')
          val time = getPreviousMidnight(now) + 3600 * hours.toInt() +
            60 * minutes.toInt() + Math.round(Math.random() * 60)
          if (time > now) time - DAY_SECONDS else time
        }
        trimmedEditedStartTime.length == 10 -> trimmedEditedStartTime.toLong() // Unix timestamp
        else -> oldStartTime + trimmedEditedStartTime.toInt() * 60 // Minute delta
      }
      if (enteredTime > 15e8 && enteredTime <= epochSeconds()) enteredTime else null
    } catch (e: Exception) {
      null
    }
    if (newStartTime == null) {
      App.toast("Invalid start time")
      return this
    }

    // Edit entry
    val entryIndex = entries.indexOfFirst { it.startTime == oldStartTime }
    val oldEntry = entries[entryIndex]
    val newEntries = entries.toMutableList().apply {
      this[entryIndex] = Entry(newStartTime, sanitizedActivity, oldEntry.latLong, sanitizedNote)
    }

    App.toast("Updated entry")
    return copy(entries = normalizeAndSave(newEntries, currentActivityStartTime))
  }

  fun withNewEntry(activity: String, note: String?, latLong: List<Double>?): History {
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    val entry = Entry(currentActivityStartTime, sanitizedActivity, latLong, sanitizedNote)
    val newEntries = entries.toMutableList().apply { add(entry) }
    val nextStartTime = epochSeconds()
    return copy(
      currentActivityStartTime = nextStartTime,
      entries = normalizeAndSave(newEntries, nextStartTime)
    )
  }

  fun withoutEntry(startTime: Long) = copy(
    entries = normalizeAndSave(
      entries.filter { it.startTime != startTime }, currentActivityStartTime
    )
  )

  companion object {
    private val file = File("/storage/emulated/0/Sync/chronofile.tsv")
    private val locationClient by lazy {
      LocationServices.getFusedLocationProviderClient(App.ctx)
    }

    private fun normalizeAndSave(
      entries: Collection<Entry>,
      currentActivityStartTime: Long
    ) = entries.toMutableList().apply {
      // Normalize
      Log.d(TAG, "Normalizing entries")
      sortBy { it.startTime }
      var lastSeenActivityAndNote: Pair<String, String?>? = null
      removeAll {
        val activityAndNote = Pair(it.activity, it.note)
        val shouldRemove = activityAndNote == lastSeenActivityAndNote
        lastSeenActivityAndNote = activityAndNote
        shouldRemove
      }

      // Save
      AsyncTask.execute {
        Log.d(TAG, "Saving history")
        val lines = mutableListOf<String>()
        forEach { lines += it.toTsvRow() }
        lines += "\t\t\t\t$currentActivityStartTime\n"
        val textToWrite = lines.joinToString("")
        if (file.exists() && file.readText() == textToWrite) {
          Log.d(TAG, "File unchanged; skipping write")
        } else {
          file.writeText(textToWrite)
        }
      }
    }.toList()

    private fun sanitizeActivityAndNote(
      activity: String,
      note: String?
    ) = Pair(activity.trim(), if (note.isNullOrBlank()) null else note?.trim())

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

    /** Acquires current location before dispatching action to create new entry */
    fun addEntry(activity: String, note: String?) {
      getLocation {
        Store.dispatch(Action.AddEntry(activity, note, it?.toList()))
        App.toast("Recorded $activity")
      }
    }

    fun fromFile(): History {
      // Ensure file exists
      var currentActivityStartTime = epochSeconds()
      val lines = if (file.exists()) file.readLines() else listOf(
        "\t\t\t\t$currentActivityStartTime\n".also { file.writeText(it) }
      )

      // Parse lines
      val entries = mutableListOf<Entry>()
      lines.forEach {
        if (it.isEmpty()) {
          return@forEach
        }

        val (activity, lat, long, note, startTime) = it.split("\t")
        val latLong = if (lat.isNotEmpty() && long.isNotEmpty()) listOf(lat.toDouble(), long.toDouble()) else null
        if (activity.isNotEmpty()) {
          entries += Entry(startTime.toLong(), activity, latLong, if (note.isEmpty()) null else note)
        } else {
          currentActivityStartTime = startTime.toLong()
        }
      }

      return History(
        normalizeAndSave(entries, currentActivityStartTime), currentActivityStartTime
      )
    }
  }
}
