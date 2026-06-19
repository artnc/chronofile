// © Art Chaidarun

package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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

    private val mainScope = MainScope()

    fun toast(message: String) {
      mainScope.launch { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
    }
  }
}
