package com.chaidarun.chronofile

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.chaidarun.chronofile.databinding.ActivityMainBinding
import com.chaidarun.chronofile.databinding.FormNfcBinding
import com.chaidarun.chronofile.databinding.FormSearchBinding

class MainActivity : BaseActivity() {
  val binding by viewBinding(ActivityMainBinding::inflate)
  private var nfcFlow: NfcFlow? = null

  private data class NfcFlow(
    val id1: String,
    val id2: String? = null,
    val dialog: AlertDialog,
    val binding: FormNfcBinding
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSupportActionBar(binding.toolbar)

    hydrateStoreFromFiles()

    // Hook up list view
    binding.historyList.layoutManager =
      LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
    binding.historyList.adapter = HistoryListAdapter(this@MainActivity)

    // Set up listeners
    binding.changeSaveDirButton.setOnClickListener { requestStorageAccess() }
    binding.grantLocationButton.setOnClickListener {
      ActivityCompat.requestPermissions(this@MainActivity, APP_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }
    binding.addEntry.setOnClickListener {
      History.addEntry(
        binding.addEntryActivity.text.toString(),
        binding.addEntryNote.text.toString()
      )
      binding.addEntryActivity.text.clear()
      binding.addEntryNote.text.clear()
      currentFocus?.clearFocus()
    }
    binding.addEntryActivity.addTextChangedListener(
      afterTextChanged = {
        val shouldEnable = binding.addEntryActivity.text.toString().isNotBlank()
        binding.addEntry.alpha = if (shouldEnable) 1f else 0.5f
        binding.addEntry.isEnabled = shouldEnable
      }
    )

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

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null && intent.action in NFC_INTENT_ACTIONS) {
      processNfcIntent(intent)
    }
  }

  override fun onResume() {
    super.onResume()
    binding.historyList.adapter?.notifyDataSetChanged() // Reformat times in case time zone changed
    if (intent.action in NFC_INTENT_ACTIONS) {
      processNfcIntent(intent)
    }
  }

  @OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
  private fun processNfcIntent(intent: Intent) {
    // Prevent intent from being processed by multiple lifecycle events - fixes a bug where
    // backgrounding and then foregrounding calls onResume with the same intent again
    // https://stackoverflow.com/a/30836555
    setIntent(Intent())

    val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
    val id = tag.id.toHexString().uppercase()
    Log.i(TAG, "Detected NFC tag: $id")
    val nfcFlow = nfcFlow
    val entryToAdd = Store.state.config?.nfcTags?.get(id)
    when {
      entryToAdd != null -> History.addEntry(entryToAdd[0], entryToAdd.getOrNull(1))
      nfcFlow == null -> {
        // Step 1 of NFC tag registration flow
        val binding = FormNfcBinding.inflate(LayoutInflater.from(this), null, false)
        val dialog =
          AlertDialog.Builder(this, R.style.MyAlertDialogTheme)
            .setTitle("Found new NFC tag")
            .setView(binding.root)
            .setOnDismissListener { this@MainActivity.nfcFlow = null }
            .setPositiveButton("OK") { _, _ ->
              val nfcFlow = this@MainActivity.nfcFlow
              if (nfcFlow != null && nfcFlow.id1 == nfcFlow.id2) {
                val inputs = mutableListOf(nfcFlow.binding.formNfcActivity.text.toString())
                val note = nfcFlow.binding.formNfcNote.text.toString()
                if (note.isNotBlank()) {
                  inputs.add(note)
                }
                Store.dispatch(Action.RegisterNfcTag(id, inputs))
                App.toast("NFC tag registered - try tapping it!")
              }
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(Dialog.BUTTON_POSITIVE).visibility = View.GONE
        this.nfcFlow = NfcFlow(id1 = id, dialog = dialog, binding = binding)
      }
      nfcFlow.id1 != id -> {
        // Error screen
        nfcFlow.dialog.setTitle("Unusable NFC tag")
        nfcFlow.binding.formNfcTapAgain.visibility = View.GONE
        nfcFlow.binding.formNfcMismatch.visibility = View.VISIBLE
        nfcFlow.dialog.getButton(Dialog.BUTTON_POSITIVE).visibility = View.VISIBLE
        nfcFlow.dialog.getButton(Dialog.BUTTON_NEGATIVE).visibility = View.GONE
      }
      else -> {
        // Step 2 of NFC tag registration flow
        nfcFlow.dialog.setTitle("Register NFC tag")
        nfcFlow.binding.formNfcTapAgain.visibility = View.GONE
        nfcFlow.binding.formNfcEnterInfo.visibility = View.VISIBLE
        val okButton = nfcFlow.dialog.getButton(Dialog.BUTTON_POSITIVE)
        nfcFlow.binding.formNfcActivity.addTextChangedListener(
          afterTextChanged = {
            okButton.isEnabled = nfcFlow.binding.formNfcActivity.text.toString().isNotBlank()
          }
        )
        nfcFlow.binding.formNfcInputs.visibility = View.VISIBLE
        nfcFlow.binding.formNfcActivity.requestFocus()
        okButton.isEnabled = false
        okButton.visibility = View.VISIBLE
        this.nfcFlow = nfcFlow.copy(id2 = id)
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
    private val NFC_INTENT_ACTIONS =
      arrayOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED
      )
    private const val PERMISSION_REQUEST_CODE = 1
    private const val STORAGE_REQUEST_CODE = 2
  }
}
