package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.view.Gravity
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

    fun toast(message: String) =
      Toast.makeText(ctx, message, Toast.LENGTH_LONG)
        .apply {
          val dpOffset = 3.5
          // https://developer.android.com/training/multiscreen/screendensities#dips-pels
          val pxOffset = (dpOffset * ctx.resources.displayMetrics.density + 0.5f).toInt()
          setGravity(Gravity.TOP, 0, pxOffset)
        }
        .show()
  }
}
