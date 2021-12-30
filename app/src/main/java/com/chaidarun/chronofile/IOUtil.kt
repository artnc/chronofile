package com.chaidarun.chronofile

import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import java.io.File

object IOUtil {
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
}
