package com.chaidarun.chronofile

/**
 * Greedily trims matching character sequences from both the start and the end of two objects'
 * string representations, returning the resulting substrings as the diff
 */
fun dumbDiff(objA: Any, objB: Any): String {
  val a = objA.toString()
  val b = objB.toString()
  if (a == b) {
    return "no change"
  }
  val lenA = a.length
  val lenB = b.length
  val range = 0 until Math.min(lenA, lenB)
  val startSame = range.takeWhile { a[it] == b[it] }.count()
  val endSame = range.takeWhile { a[lenA - 1 - it] == b[lenB - 1 - it] }.count()
  return "`${a.substring(startSame, lenA - endSame)}` => `${b.substring(startSame, lenB - endSame)}`"
}
