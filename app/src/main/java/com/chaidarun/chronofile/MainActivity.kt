package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
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
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.design.longSnackbar


class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
      setHistory()
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
      1 -> {
        if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          setHistory()
        } else {
          longSnackbar(fab, "Permission denied :(")
        }
      }
    }
  }

  private fun setHistory() {
    val history = History()
    (application as App).history = history

    historyList.layoutManager = LinearLayoutManager(this).apply {
      reverseLayout = true
      stackFromEnd = true
    }
    historyList.adapter = HistoryListAdapter(history, {
      history.addEntry(it.activity)
      longSnackbar(fab, "Recorded ${it.activity}")
    })

    fab.setOnClickListener {
      val input = EditText(this)
      with(AlertDialog.Builder(this)) {
        setTitle("Last ${history.getFuzzyTimeSinceLastEntry()}")
        input.inputType = InputType.TYPE_CLASS_TEXT
        setView(input)
        setPositiveButton("OK", { _, _ ->
          history.addEntry(input.text.toString())
          historyList.adapter.notifyDataSetChanged()
        })
        setNegativeButton("Cancel", { dialog, _ -> dialog.cancel() })
        show()
      }
    }
  }
}
