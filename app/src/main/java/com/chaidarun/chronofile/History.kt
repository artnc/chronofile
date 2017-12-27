package com.chaidarun.chronofile

import java.io.File
import java.util.*

class History {

    val entries = mutableListOf<Entry>()
    private var nextStartTime = getTimestamp()
    private val mFile = File("/storage/emulated/0/Sync/chronofile.csv")

    init {
        loadHistoryFromFile()
    }

    fun addEntry(activity: String) {
        entries.add(Entry(nextStartTime, activity))
        normalizeEntries()
        nextStartTime = getTimestamp()
        saveHistoryToDisk()
    }

    fun getFuzzyTimeSinceLastEntry(): String {
        val elapsedSeconds = getTimestamp() - nextStartTime
        val elapsedMinutes = elapsedSeconds / 60
        val elapsedHours = elapsedMinutes / 60
        return when {
            elapsedHours > 0 -> "$elapsedHours hours"
            elapsedMinutes > 0 -> "$elapsedMinutes minutes"
            else -> "$elapsedSeconds seconds"
        }
    }

    private fun loadHistoryFromFile() {
        nextStartTime = getTimestamp()
        if (!mFile.exists()) {
            mFile.writeText("$nextStartTime")
        }
        entries.clear()
        mFile.readLines().forEach {
            val pieces = it.split(',')
            val startTime = pieces[0].toLong()
            when (pieces.size) {
                1 -> nextStartTime = startTime
                else -> entries.add(Entry(startTime, pieces[1]))
            }
        }
        normalizeEntries()
        saveHistoryToDisk()
    }

    private fun saveHistoryToDisk() {
        val lines = mutableListOf<String>()
        entries.forEach { lines.add("${it.startTime},${it.activity}") }
        lines.add(nextStartTime.toString())
        mFile.writeText(lines.joinToString("\n"))
    }

    private fun normalizeEntries() {
        entries.sortBy { it.startTime }
        val duplicateIndices = Stack<Int>()
        var lastSeenActivity: String? = null
        entries.forEachIndexed { index, entry ->
            if (entry.activity == lastSeenActivity) {
                duplicateIndices.push(index)
            } else {
                lastSeenActivity = entry.activity
            }
        }
        while (!duplicateIndices.isEmpty()) {
            entries.removeAt(duplicateIndices.pop())
        }
    }

    private fun getTimestamp() = System.currentTimeMillis() / 1000
}
