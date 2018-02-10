package com.chaidarun.chronofile

import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.gson.GsonBuilder
import org.jetbrains.anko.toast
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
      App.ctx.toast("Invalid start time")
      return this
    }

    // Edit entry
    val entryIndex = entries.indexOfFirst { it.startTime == oldStartTime }
    val oldEntry = entries[entryIndex]
    val newEntries = entries.toMutableList().apply {
      this[entryIndex] = Entry(newStartTime, sanitizedActivity, oldEntry.latLong, sanitizedNote)
    }

    App.ctx.toast("Updated entry")
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

  fun withoutEntries(startTimes: Collection<Long>) = copy(entries = normalizeAndSave(
    entries.filter { it.startTime !in startTimes }, currentActivityStartTime
  )
  )

  companion object {
    private val file = File("/storage/emulated/0/Sync/chronofile.jsonl")
    private val gson by lazy {
      GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create()
    }
    private val locationClient by lazy {
      LocationServices.getFusedLocationProviderClient(App.ctx)
    }

    private fun normalizeAndSave(
      entries: Collection<Entry>,
      currentActivityStartTime: Long
    ) = entries.toMutableList().apply {
      // Normalize
      Log.d(TAG, "Normalizing entries")
      val config = Store.state.config
      replaceAll { it.snapToKnownLocation(config) }
      sortBy { it.startTime }
      var lastSeenActivityAndNote: Pair<String, String?>? = null
      removeAll {
        val activityAndNote = Pair(it.activity, it.note)
        val shouldRemove = activityAndNote == lastSeenActivityAndNote
        lastSeenActivityAndNote = activityAndNote
        shouldRemove
      }

      // Save
      Log.d(TAG, "Saving history")
      val lines = mutableListOf<String>()
      forEach { lines += gson.toJson(it) }
      lines += gson.toJson(PlaceholderEntry(currentActivityStartTime))
      val textToWrite = lines.joinToString("") { "$it\n" }
      if (file.exists() && file.readText() == textToWrite) {
        Log.d(TAG, "File unchanged; skipping write")
      } else {
        file.writeText(textToWrite)
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
        App.ctx.toast("Recorded $activity")
      }
    }

    fun fromFile(): History {
      // Ensure file exists
      var currentActivityStartTime = epochSeconds()
      val lines = if (file.exists()) file.readLines() else listOf(
        gson.toJson(PlaceholderEntry(currentActivityStartTime)).apply { file.writeText(this) }
      )

      // Parse lines
      val entries = mutableListOf<Entry>()
      lines.forEach {
        if (',' in it) {
          entries += gson.fromJson(it, Entry::class.java)
        } else if (it.trim().isNotEmpty()) {
          currentActivityStartTime = gson.fromJson(it, PlaceholderEntry::class.java).startTime
        }
      }

      return History(
        normalizeAndSave(entries, currentActivityStartTime), currentActivityStartTime
      )
    }
  }
}
