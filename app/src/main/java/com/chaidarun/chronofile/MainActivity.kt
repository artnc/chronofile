package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.android.synthetic.main.content_main.addEntry
import kotlinx.android.synthetic.main.content_main.addEntryActivity
import kotlinx.android.synthetic.main.content_main.addEntryNote
import kotlinx.android.synthetic.main.content_main.historyList

class MainActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Set up UI
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)

    // Ensure required permissions are granted
    if (APP_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
      }
    ) {
      init()
    } else {
      ActivityCompat.requestPermissions(this, APP_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.action_about ->
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/artnc/chronofile")))
      R.id.action_edit_config_file -> startActivity(Intent(this, EditorActivity::class.java))
      R.id.action_refresh -> {
        hydrateStoreFromFiles()
        App.toast("Reloaded history and config from disk")
      }
      R.id.action_stats -> startActivity(Intent(this, GraphActivity::class.java))
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      PERMISSION_REQUEST_CODE ->
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          init()
        } else {
          App.toast("Permission denied :(")
        }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
      STORAGE_REQUEST_CODE ->
        if (resultCode == RESULT_OK) {
          App.instance.storageDirectory = data!!.data
          init()
        } else {
          App.toast("No storage available :(")
        }
    }
  }

  private fun requestStorage() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      putExtra(DocumentsContract.EXTRA_PROMPT, "Pick a directory")
    }

    startActivityForResult(intent, STORAGE_REQUEST_CODE)
  }

  private fun hydrateStoreFromFiles() {
    // TODO use storage access framework for config file
    Store.dispatch(Action.SetConfigFromFile(Config()))
    Store.dispatch(Action.SetHistory(History.fromFile()))
  }

  private fun init() {
    try {
      hydrateStoreFromFiles()
    } catch (e: IllegalArgumentException) {
      requestStorage()
      return
    }

    // Hook up list view
    historyList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
    historyList.adapter = HistoryListAdapter(this)

    // Set up listeners
    disposables =
      CompositeDisposable().apply {
        add(
          RxView.clicks(addEntry).subscribe {
            History.addEntry(addEntryActivity.text.toString(), addEntryNote.text.toString())
            addEntryActivity.text.clear()
            addEntryNote.text.clear()
            currentFocus?.clearFocus()
          }
        )
        add(
          RxTextView.afterTextChangeEvents(addEntryActivity).subscribe {
            addEntry.isEnabled = !addEntryActivity.text.toString().isBlank()
          }
        )
      }
  }

  companion object {
    val APP_PERMISSIONS =
      arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    const val PERMISSION_REQUEST_CODE = 1
    const val STORAGE_REQUEST_CODE = 2
  }
}
