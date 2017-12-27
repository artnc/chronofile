package com.chaidarun.chronofile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.design.longSnackbar
import java.io.File


class MainActivity : AppCompatActivity() {

    private var mLayout: View? = null
    private var mAdapter: HistoryListAdapter? = null
    private var nextStartTime: Long? = null
    private val mFile = File("/storage/emulated/0/Sync/chronofile.csv")
    private val entries = mutableListOf<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mLayout = fab

        history_list.layoutManager = LinearLayoutManager(this)
        mAdapter = HistoryListAdapter(entries)
        history_list.adapter = mAdapter

        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val builder = AlertDialog.Builder(this)
            val elapsedSeconds = getTimestamp() - nextStartTime!!
            builder.setTitle("Last ${getFuzzyTime(elapsedSeconds)}")
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)
            builder.setPositiveButton("OK", { _, _ -> addEntry(input.text.toString()) })
            builder.setNegativeButton("Cancel", { dialog, _ -> dialog.cancel() })
            builder.show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            loadHistoryFromDisk()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds entries to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> {
                if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadHistoryFromDisk()
                } else {
                    longSnackbar(mLayout!!, "Permission denied :(")
                }
            }
        }
    }

    private fun addEntry(activity: String) {
        entries.add(Entry(nextStartTime!!, activity))
        nextStartTime = getTimestamp()
        mAdapter!!.notifyDataSetChanged()
        saveHistoryToDisk()
    }

    private fun loadHistoryFromDisk() {
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
        entries.sortBy { it.startTime }
        mAdapter!!.notifyDataSetChanged()
        saveHistoryToDisk()
    }

    private fun saveHistoryToDisk() {
        val lines = mutableListOf<String>()
        entries.forEach { lines.add("${it.startTime},${it.activity}") }
        lines.add(nextStartTime.toString())
        mFile.writeText(lines.joinToString("\n"))
    }

    private fun getTimestamp() = System.currentTimeMillis() / 1000

    private fun getFuzzyTime(seconds: Long): String = when {
        seconds > 3600 -> "${seconds / 3600} hours"
        seconds > 60 -> "${seconds / 60} minutes"
        else -> "$seconds seconds"
    }
}
