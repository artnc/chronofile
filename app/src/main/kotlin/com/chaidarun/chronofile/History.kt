// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import com.google.android.gms.location.LocationServices
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

data class History(val entries: List<Entry>, val currentActivityStartTime: Long) {

  fun withEditedEntry(
    oldStartTime: Long,
    editedStartTime: String,
    activity: String,
    note: String?,
  ): History {
    // Collect inputs
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    val newStartTime =
      try {
        val trimmedEditedStartTime = editedStartTime.trim()
        val enteredTime =
          when {
            trimmedEditedStartTime == "" -> oldStartTime
            ':' in trimmedEditedStartTime -> {
              val now = epochSeconds()
              val (hours, minutes) = trimmedEditedStartTime.split(':')
              val time =
                getPreviousMidnight(now) +
                  3600 * hours.toInt() +
                  60 * minutes.toInt() +
                  Random.nextLong(60)
              if (time > now) time - DAY_SECONDS else time
            }
            trimmedEditedStartTime.length == 10 -> trimmedEditedStartTime.toLong() // Unix timestamp
            else -> oldStartTime + trimmedEditedStartTime.toInt() * 60 // Minute delta
          }
        if (enteredTime > 15e8 && enteredTime <= epochSeconds()) enteredTime else null
      } catch (_: Exception) {
        null
      }
    if (newStartTime == null) {
      App.toast("Invalid start time")
      return this
    }

    // Edit entry
    val entryIndex = entries.indexOfFirst { it.startTime == oldStartTime }
    val oldEntry = entries[entryIndex]
    val newEntries =
      entries.toMutableList().apply {
        this[entryIndex] = Entry(newStartTime, sanitizedActivity, oldEntry.latLon, sanitizedNote)
      }

    App.toast("Updated entry")
    return copy(entries = normalizeAndSave(newEntries, currentActivityStartTime))
  }

  fun withNewEntry(activity: String, note: String?, latLon: Pair<Double, Double>?): History {
    val (sanitizedActivity, sanitizedNote) = sanitizeActivityAndNote(activity, note)
    val entry = Entry(currentActivityStartTime, sanitizedActivity, latLon, sanitizedNote)
    val newEntries = entries.toMutableList().apply { add(entry) }
    val nextStartTime = epochSeconds()
    return copy(
      currentActivityStartTime = nextStartTime,
      entries = normalizeAndSave(newEntries, nextStartTime),
    )
  }

  fun withoutEntry(startTime: Long) =
    copy(
      entries =
        normalizeAndSave(entries.filter { it.startTime != startTime }, currentActivityStartTime)
    )

  companion object {
    private const val FILENAME = "chronofile.tsv"
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(App.ctx) }

    private fun normalizeAndSave(
      entries: Collection<Entry>,
      currentActivityStartTime: Long,
      save: Boolean = true,
    ): List<Entry> {
      // Normalize
      Log.i(TAG, "Normalizing entries")
      val normalized = entries.toMutableList()
      normalized.sortBy { it.startTime }
      var lastSeenActivity: String? = null
      var lastSeenNote: String? = null
      normalized.removeAll {
        val shouldRemove = it.activity == lastSeenActivity && it.note == lastSeenNote
        lastSeenActivity = it.activity
        lastSeenNote = it.note
        shouldRemove
      }

      // Save, building the full TSV string inside the IO coroutine (off the caller's main thread).
      // The serialized snapshot is the just-normalized list, which is never mutated after this
      // since
      // every mutation builds a fresh list, so the deferred read stays consistent
      if (save) {
        IOUtil.writeFile(FILENAME) {
          normalized.joinToString("") { it.toTsvRow() } + "\t\t\t\t$currentActivityStartTime\n"
        }
      }

      return normalized
    }

    private fun sanitizeActivityAndNote(activity: String, note: String?) =
      Pair(activity.trim(), if (note.isNullOrBlank()) null else note.trim())

    /** Acquires the device's last known location, or null if unavailable or not permitted */
    suspend fun getCurrentLocation(): Pair<Double, Double>? =
      try {
        locationClient.lastLocation.await()?.let { Pair(it.latitude, it.longitude) }
      } catch (_: Exception) {
        Log.i(TAG, "Failed to get location")
        null
      }

    fun fromFile(): History {
      // Read lines
      var currentActivityStartTime = epochSeconds()
      val lines = IOUtil.readFile(FILENAME)?.lines() ?: listOf("\t\t\t\t$currentActivityStartTime")

      // Parse lines
      val entries = mutableListOf<Entry>()
      for (line in lines) {
        if (line.isEmpty()) continue

        val (activity, lat, lon, note, startTime) = line.split("\t")
        val latLon =
          if (lat.isNotEmpty() && lon.isNotEmpty()) Pair(lat.toDouble(), lon.toDouble()) else null
        if (activity.isNotEmpty()) {
          entries += Entry(startTime.toLong(), activity, latLon, if (note.isEmpty()) null else note)
        } else {
          currentActivityStartTime = startTime.toLong()
        }
      }

      // Don't save() on load: we'd re-serialize the whole file and read it back just to produce a
      // no-op write. The next mutation persists the normalized form anyway
      return History(
        normalizeAndSave(entries, currentActivityStartTime, save = false),
        currentActivityStartTime,
      )
    }
  }
}
