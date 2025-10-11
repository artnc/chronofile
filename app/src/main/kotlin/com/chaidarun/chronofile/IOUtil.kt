// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.AsyncTask
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis

object IOUtil {
  /** Name of SharedPreferences key for recording the user's desired save directory */
  const val STORAGE_DIR_PREF = "STORAGE_DIR"

  /**
   * Ideally this would use [android.preference.PreferenceManager.getDefaultSharedPreferences], but
   * that's deprecated and I'm not going to install a compat lib just to build this one string...
   * https://stackoverflow.com/q/56833657
   */
  private fun getPrefs() =
    App.ctx.getSharedPreferences("${App.ctx.packageName}_preferences", MODE_PRIVATE)

  fun getPref(key: String) = getPrefs().getString(key, null)

  fun setPref(key: String, value: String) = getPrefs().edit { putString(key, value) }

  private fun getStorageUri() = getPref(STORAGE_DIR_PREF)?.toUri()

  // https://stackoverflow.com/a/64863166
  private fun getStorageDir() = getStorageUri()?.let { DocumentFile.fromTreeUri(App.ctx, it) }

  // https://developer.android.com/training/data-storage/shared/documents-files#input_stream
  private fun readDocumentFile(documentFile: DocumentFile) =
    try {
      App.ctx.contentResolver.openInputStream(documentFile.uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }

  /**
   * By default, Android will forget that the user granted permission after reboot :| So this method
   * does two things: it persists the granted permission to survive reboot, and it returns a boolean
   * indicating whether the permission is currently granted
   */
  fun persistAndCheckStoragePermission() =
    try {
      App.ctx.contentResolver.takePersistableUriPermission(
        getStorageUri()!!,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }

  fun readFile(filename: String): String? {
    Log.i(TAG, "Reading $filename")
    var result: String? = null
    val elapsedMs = measureTimeMillis {
      result = readDocumentFile(getStorageDir()?.findFile(filename) ?: return@measureTimeMillis)
    }
    Log.i(
      TAG,
      if (result == null) "Failed to read $filename" else "Read $filename in $elapsedMs ms",
    )
    return result
  }

  fun writeFile(filename: String, text: String) {
    AsyncTask.execute {
      Log.i(TAG, "Writing $filename")
      var isSuccess = false
      val elapsedMs = measureTimeMillis {
        val storageDir = getStorageDir() ?: return@execute
        var documentFile = storageDir.findFile(filename)
        when {
          documentFile == null -> {
            documentFile =
              storageDir.createFile(
                when {
                  filename.endsWith(".json") -> "application/json"
                  filename.endsWith(".tsv") -> "text/tab-separated-values"
                  else -> "text/plain"
                },
                filename,
              )
            if (documentFile == null) {
              Log.i(TAG, "Failed to create $filename")
              return@execute
            }
            Log.i(TAG, "Created $filename")
          }
          readDocumentFile(documentFile) == text -> {
            Log.i(TAG, "File $filename unchanged; skipping write")
            return@execute
          }
        }

        // Google's docs are somewhat buggy in specifying a mode of "w" instead of "wt". Without
        // "t" (truncate), writing the byte "C" to a file containing "AB" can result in "CB"
        // https://developer.android.com/training/data-storage/shared/documents-files#edit
        // https://developer.android.com/reference/android/content/ContentResolver#openFileDescriptor(android.net.Uri,%20java.lang.String,%20android.os.CancellationSignal)
        try {
          App.ctx.contentResolver.openFileDescriptor(documentFile.uri, "wt")?.use {
            FileOutputStream(it.fileDescriptor).use { it.write(text.toByteArray()) }
            isSuccess = true
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
      Log.i(TAG, "Wrote $filename in $elapsedMs ms")
      if (!isSuccess) {
        App.toast("Failed to save $filename :(")
      }
    }
  }
}
