package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.form_entry.view.*
import org.jetbrains.anko.toast


class MainActivity : BaseActivity() {

  companion object {
    val APP_PERMISSIONS = arrayOf(
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    val PERMISSION_REQUEST_CODE = 1
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    if (APP_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
      setHistory()
    } else {
      ActivityCompat.requestPermissions(this, APP_PERMISSIONS, PERMISSION_REQUEST_CODE)
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
      R.id.action_graph -> {
        val intent = Intent(this, PieActivity::class.java)
        startActivity(intent)
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    when (requestCode) {
      PERMISSION_REQUEST_CODE -> {
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          setHistory()
        } else {
          toast("Permission denied :(")
        }
      }
    }
  }

  private fun setHistory() {
    val history = History()
    (application as App).history = history

    val addEntryCallback = { entry: Entry ->
      historyList.adapter.notifyDataSetChanged()
      toast("Recorded ${entry.activity}")
    }

    historyList.layoutManager = LinearLayoutManager(this).apply {
      reverseLayout = true
      stackFromEnd = true
    }
    historyList.adapter = HistoryListAdapter(history, {
      history.addEntry(it.activity, it.note, addEntryCallback)
    })

    fab.setOnClickListener {
      val view = layoutInflater.inflate(R.layout.form_entry, null)
      with(AlertDialog.Builder(this)) {
        setTitle("Last ${history.getFuzzyTimeSinceLastEntry()}")
        setView(view)
        setPositiveButton("OK", { _, _ ->
          history.addEntry(view.formEntryActivity.text.toString(), view.formEntryNote.text.toString(), addEntryCallback)
        })
        setNegativeButton("Cancel", { dialog, _ -> dialog.cancel() })
        show()
      }
    }

    swipeRefresh.setOnRefreshListener {
      history.loadHistoryFromFile()
      toast("Reloaded history from disk")
      swipeRefresh.isRefreshing = false
    }
  }
}
