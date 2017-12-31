package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import uk.co.chrisjenx.calligraphy.CalligraphyConfig

class App : Application() {

  val typeface: Typeface by lazy { Typeface.createFromAsset(assets, "fonts/Exo2-Regular.otf") }

  override fun onCreate() {
    super.onCreate()
    CalligraphyConfig.initDefault(CalligraphyConfig.Builder().setDefaultFontPath("fonts/Exo2-Regular.otf").setFontAttrId(R.attr.fontPath).build())
    instance = this
    ctx = this.applicationContext
  }

  companion object {
    lateinit var instance: App
      private set
    lateinit var ctx: Context
      private set
  }
}
