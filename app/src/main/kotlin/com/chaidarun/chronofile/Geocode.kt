// © Art Chaidarun

package com.chaidarun.chronofile

import android.location.Geocoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of resolved, display-ready addresses keyed by (lat, lon), reused across screens
 */
private val geocodeCache = mutableMapOf<Pair<Double, Double>, String>()

/** Trailing zip code and country, stripped before caching so every caller gets the short form */
private val addressSuffixRegex = Regex(",? \\d{5},? USA?$")

/**
 * Reverse-geocodes an entry's coordinates into a short human-readable address, or null if
 * unavailable
 */
suspend fun geocodeEntry(entry: Entry): String? {
  val latLon = entry.latLon ?: return null
  geocodeCache[latLon]?.let {
    return it
  }
  return withContext(Dispatchers.IO) {
      try {
        @Suppress("DEPRECATION")
        Geocoder(App.ctx, Locale.US)
          .getFromLocation(latLon.first, latLon.second, 1)
          ?.firstOrNull()
          ?.getAddressLine(0)
      } catch (_: Exception) {
        null
      }
    }
    // Strip the trailing zip code and country, then cache only successful lookups so transient
    // failures get retried
    ?.replace(addressSuffixRegex, "")
    ?.also { geocodeCache[latLon] = it }
}

/**
 * Reverse-geocodes [entry]'s coordinates into a dialog title, showing "Locating…" until it resolves
 * and "No location" if it can't be resolved
 */
@Composable
fun rememberGeocodedTitle(entry: Entry): State<String> =
  produceState("Locating…", entry) { value = geocodeEntry(entry) ?: "No location" }
