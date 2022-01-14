package com.chaidarun.chronofile

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.widget.Toast
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump

class App : Application() {

  val typeface: Typeface by lazy { Typeface.createFromAsset(assets, FONT_PATH) }
  lateinit var preferences: SharedPreferences

  override fun onCreate() {
    instance = this
    super.onCreate()

    preferences = getSharedPreferences("chronofile", MODE_PRIVATE)

    // Set global default font
    ViewPump.init(
      ViewPump.builder()
        .addInterceptor(
          CalligraphyInterceptor(
            CalligraphyConfig.Builder()
              .setDefaultFontPath(FONT_PATH)
              .setFontAttrId(R.attr.fontPath)
              .build()
          )
        )
        .build()
    )
  }

  var storageDirectory: Uri?
    get() {
      val uri = preferences.getString("storage_dir", null)
      return if (uri.isNullOrEmpty()) null else Uri.parse(uri)
    }
    set(value) {
      preferences.edit()
        .putString("storage_dir", value.toString())
        .apply()
    }

  companion object {
    lateinit var instance: App
      private set
    val ctx: Context
      get() = instance.applicationContext

    private const val FONT_PATH = "fonts/Exo2-Regular.otf"

    fun toast(message: String) =
      Toast.makeText(ctx, message, Toast.LENGTH_SHORT)
        .apply {
          val dpOffset = 3.5
          // https://developer.android.com/training/multiscreen/screendensities#dips-pels
          val pxOffset = (dpOffset * ctx.resources.displayMetrics.density + 0.5f).toInt()
          setGravity(Gravity.TOP, 0, pxOffset)
        }
        .show()
  }
}
