package com.chaidarun.chronofile

/**
 * Greedily trims matching character sequences from both the start and the end of two objects'
 * string representations, returning the resulting substrings as the diff
 */
fun dumbDiff(objA: Any, objB: Any): String {
  var a = objA.toString()
  var b = objB.toString()
  if (a == b) {
    return "no change"
  }
  val startSame = (0 until Math.min(a.length, b.length)).takeWhile { a[it] == b[it] }.count()
  a = a.substring(startSame)
  b = b.substring(startSame)
  val lenA = a.length
  val lenB = b.length
  val endSame = (0 until Math.min(lenA, lenB)).takeWhile {
    a[lenA - 1 - it] == b[lenB - 1 - it]
  }.count()
  return "`${a.substring(0, lenA - endSame)}` => `${b.substring(0, lenB - endSame)}`"
}
