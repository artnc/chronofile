package com.chaidarun.chronofile

import java.text.SimpleDateFormat
import java.util.*

/** Logging tag */
const val TAG = "Chronofile"

val DATE_FORMAT = SimpleDateFormat("EE, dd MMM yyyy", Locale.getDefault())
val TIME_FORMAT = SimpleDateFormat("H:mm", Locale.getDefault())

/** Number of seconds in a day */
const val DAY_SECONDS = 86400
