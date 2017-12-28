package com.chaidarun.chronofile

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager

open class BaseActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
}
