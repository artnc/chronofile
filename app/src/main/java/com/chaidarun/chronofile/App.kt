package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump

class App : Application() {

  val typeface: Typeface by lazy { Typeface.createFromAsset(assets, "fonts/Exo2-Regular.otf") }

  override fun onCreate() {
    super.onCreate()

    // Set global default font
    ViewPump.init(
      ViewPump.builder().addInterceptor(
        CalligraphyInterceptor(
          CalligraphyConfig.Builder()
            .setDefaultFontPath("fonts/Exo2-Regular.otf")
            .setFontAttrId(R.attr.fontPath)
            .build()
        )
      ).build()
    )

    ctx = this.applicationContext
    instance = this
  }

  companion object {
    lateinit var instance: App
      private set
    lateinit var ctx: Context
      private set

    fun toast(message: String) = Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
  }
}
