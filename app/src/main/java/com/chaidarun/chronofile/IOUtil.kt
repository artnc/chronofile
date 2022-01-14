package com.chaidarun.chronofile

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import java.io.File

object IOUtil {
  @Deprecated("Use Storage Access Framework.")
  val dir: String = Environment.getExternalStorageDirectory().absolutePath

  fun writeFile(file: File, text: String) {
    AsyncTask.execute {
      val name = file.name
      Log.i(TAG, "Saving $name}")
      if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.createNewFile()
      }
      if (file.readText() == text) {
        Log.i(TAG, "File $name unchanged; skipping write")
      } else {
        val start = System.currentTimeMillis()
        file.writeText(text)
        Log.i(TAG, "Wrote $name in ${System.currentTimeMillis() - start} ms")
      }
    }
  }

  fun writeFile(context: Context, file: Uri, text: String) {
    AsyncTask.execute {
      val stream = context.contentResolver.openOutputStream(file)
      val name = file.lastPathSegment
      Log.i(TAG, "Saving $name}")
      // TODO avoid writing if content is the same
      val start = System.currentTimeMillis()
      val output = stream!!.bufferedWriter()
      output.write(text)
      output.close()
      Log.i(TAG, "Wrote $name in ${System.currentTimeMillis() - start} ms")
    }
  }
}
