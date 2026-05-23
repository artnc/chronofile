// © Art Chaidarun

package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class App : Application() {

  override fun onCreate() {
    instance = this
    super.onCreate()
  }

  companion object {
    lateinit var instance: App
      private set

    val ctx: Context
      get() = instance.applicationContext

    private val mainHandler = Handler(Looper.getMainLooper())

    fun toast(message: String) = mainHandler.post {
      Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }
  }
}
