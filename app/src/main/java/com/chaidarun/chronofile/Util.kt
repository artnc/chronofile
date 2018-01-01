package com.chaidarun.chronofile

import android.util.Log

private const val ELLIPSIS = "..."

/**
 * Ellipsizes an object's string representation if needed so that it (including ellipsis) is at
 * most the given length
 */
fun ellipsize(obj: Any, maxLength: Int = 64) = with(obj.toString()) {
  when {
    this.length <= ELLIPSIS.length -> this
    this.length > maxLength -> this.substring(0, maxLength - ELLIPSIS.length) + ELLIPSIS
    else -> this
  }
}

/**
 * Greedily trims matching character sequences from both the start and the end of two objects'
 * string representations, returning the resulting substrings as the diff
 */
fun dumbDiff(objA: Any, objB: Any): Pair<String, String> {
  var a = objA.toString()
  var b = objB.toString()
  if (a == b) {
    return Pair("", "")
  }

  // Trim from start
  val startSame = (0 until Math.min(a.length, b.length)).takeWhile { a[it] == b[it] }.count()
  a = a.substring(startSame)
  b = b.substring(startSame)

  // Trim from end
  val lenA = a.length
  val lenB = b.length
  val endSame = (0 until Math.min(lenA, lenB)).takeWhile {
    a[lenA - 1 - it] == b[lenB - 1 - it]
  }.count()
  a = a.substring(0, lenA - endSame)
  b = b.substring(0, lenB - endSame)

  return Pair(a, b)
}

/** Gets current Unix timestamp in seconds */
fun epochSeconds() = System.currentTimeMillis() / 1000

/** Pretty-prints time given in seconds, e.g. 86461 -> "1d 1m" */
fun formatTime(seconds: Long): String {
  // Rounds to nearest minute
  val adjustedSeconds = if (seconds % 60 < 30) seconds else seconds + 60

  val pieces = mutableListOf<String>()
  val totalMinutes = adjustedSeconds / 60
  val minutes = totalMinutes % 60
  if (minutes != 0L) {
    pieces.add(0, "${minutes}m")
  }
  val totalHours = totalMinutes / 60
  val hours = totalHours % 24
  if (hours != 0L) {
    pieces.add(0, "${hours}h")
  }
  val days = totalHours / 24
  if (days != 0L) {
    pieces.add(0, "${days}d")
  }
  return pieces.joinToString(" ")
}

/** Log a message at warning level if given boolean is true, else at debug level */
fun logDW(message: String, warnIf: Boolean = true) =
  if (warnIf) Log.w(TAG, message) else Log.d(TAG, message)
