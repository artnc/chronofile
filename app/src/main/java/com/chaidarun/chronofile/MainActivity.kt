package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chaidarun.chronofile.databinding.ActivityMainBinding
import com.chaidarun.chronofile.databinding.FormSearchBinding
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.disposables.CompositeDisposable

class MainActivity : BaseActivity() {
  val binding by viewBinding(ActivityMainBinding::inflate)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSupportActionBar(binding.toolbar)

    hydrateStoreFromFiles()

    // Hook up list view
    binding.historyList.layoutManager =
      LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
    binding.historyList.adapter = HistoryListAdapter(this@MainActivity)

    // Set up listeners
    disposables =
      CompositeDisposable().apply {
        add(RxView.clicks(binding.changeSaveDirButton).subscribe { requestStorageAccess() })
        add(
          RxView.clicks(binding.grantLocationButton).subscribe {
            ActivityCompat.requestPermissions(
              this@MainActivity,
              APP_PERMISSIONS,
              PERMISSION_REQUEST_CODE
            )
          }
        )
        add(
          RxView.clicks(binding.addEntry).subscribe {
            History.addEntry(
              binding.addEntryActivity.text.toString(),
              binding.addEntryNote.text.toString()
            )
            binding.addEntryActivity.text.clear()
            binding.addEntryNote.text.clear()
            currentFocus?.clearFocus()
          }
        )
        add(
          RxTextView.afterTextChangeEvents(binding.addEntryActivity).subscribe {
            binding.addEntry.isEnabled = !binding.addEntryActivity.text.toString().isBlank()
          }
        )
      }

    // Check for missing permissions
    if (
      !APP_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
      }
    ) {
      Log.i(TAG, "Found ungranted permissions")
      binding.grantLocationBanner.visibility = View.VISIBLE
    }
    if (
      IOUtil.getPref(IOUtil.STORAGE_DIR_PREF).isNullOrEmpty() ||
        !IOUtil.persistAndCheckStoragePermission()
    ) {
      Log.i(TAG, "Found ungranted storage access")
      binding.changeSaveDirBanner.visibility = View.VISIBLE
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
      R.id.action_change_save_dir -> requestStorageAccess()
      R.id.action_search -> {
        val formBinding = FormSearchBinding.inflate(LayoutInflater.from(this), null, false)
        val view = formBinding.root
        with(AlertDialog.Builder(this, R.style.MyAlertDialogTheme)) {
          setTitle("Search timeline")
          formBinding.formSearchQuery.setText(Store.state.searchQuery ?: "")
          setView(view)
          fun search(input: String?) {
            val query = if (input.isNullOrBlank()) null else input.trim()
            Store.dispatch(Action.SetSearchQuery(query))
            binding.toolbar.title = if (query == null) "Timeline" else "\"$query\""
          }
          setPositiveButton("Go") { _, _ -> search(formBinding.formSearchQuery.text.toString()) }
          setNegativeButton("Clear") { _, _ -> search(null) }
          show()
        }
      }
      R.id.action_settings -> startActivity(Intent(this, EditorActivity::class.java))
      R.id.action_stats -> startActivity(Intent(this, GraphActivity::class.java))
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
    super.onActivityResult(requestCode, resultCode, resultData)
    when (requestCode) {
      STORAGE_REQUEST_CODE -> {
        val uri = resultData?.data
        if (resultCode != RESULT_OK || uri == null) {
          App.toast("Storage location not changed")
          return
        }
        binding.changeSaveDirBanner.visibility = View.GONE
        App.toast("Successfully changed storage location")
        IOUtil.persistAndCheckStoragePermission()
        IOUtil.setPref(IOUtil.STORAGE_DIR_PREF, uri.toString())
        hydrateStoreFromFiles()
      }
    }
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
          binding.grantLocationBanner.visibility = View.GONE
          App.toast("Permissions granted successfully :)")
        } else {
          App.toast("You denied permission :(")
        }
    }
  }

  private fun requestStorageAccess() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), STORAGE_REQUEST_CODE)
  }

  private fun hydrateStoreFromFiles() {
    Store.dispatch(Action.SetConfigFromFile(Config.fromFile()))
    Store.dispatch(Action.SetHistory(History.fromFile()))
  }

  companion object {
    private val APP_PERMISSIONS =
      arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    private const val PERMISSION_REQUEST_CODE = 1
    private const val STORAGE_REQUEST_CODE = 2
  }
}
