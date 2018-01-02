package com.chaidarun.chronofile

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
