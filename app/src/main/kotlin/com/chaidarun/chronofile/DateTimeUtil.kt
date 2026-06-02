// © Art Chaidarun

package com.chaidarun.chronofile

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** Gets current Unix timestamp in seconds */
fun epochSeconds() = System.currentTimeMillis() / 1000

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EE, d MMM yyyy", Locale.US)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", Locale.US)
private val SEARCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

fun formatDate(seconds: Long) =
  DATE_FORMAT.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))

fun formatTime(date: Date) = TIME_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()))

fun formatForSearch(seconds: Long) =
  SEARCH_FORMAT.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))

/** Pretty-prints time given in seconds, e.g. 86461 -> "1d 1m" */
fun formatDuration(seconds: Long, showDays: Boolean = false, showMinutes: Boolean = true): String {
  if (seconds < 30) return "0m"

  // Rounds to nearest minute
  val adjustedSeconds = if (seconds % 60 < 30) seconds else seconds + 60

  val pieces = mutableListOf<String>()
  val totalMinutes = adjustedSeconds / 60
  val minutes = totalMinutes % 60
  if (showMinutes && minutes != 0L) {
    pieces.add(0, "${minutes}m")
  }
  val totalHours =
    if (showMinutes) totalMinutes / 60 else (totalMinutes.toDouble() / 60).roundToLong()
  val hours = if (showDays) totalHours % 24 else totalHours
  if (hours != 0L) {
    pieces.add(0, "${hours}h")
  }
  if (showDays) {
    val days = totalHours / 24
    if (days != 0L) {
      pieces.add(0, "${days}d")
    }
  }
  return pieces.joinToString(" ")
}

fun getDate(timestamp: Long): LocalDate =
  LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()).toLocalDate()

/** Gets the timestamp of the last midnight that occurred before the given timestamp */
fun getPreviousMidnight(timestamp: Long) =
  getDate(timestamp).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

fun getDayOfWeek(timestamp: Long): DayOfWeek = getDate(timestamp).dayOfWeek
