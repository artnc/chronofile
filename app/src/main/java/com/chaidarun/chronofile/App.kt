package com.chaidarun.chronofile

import android.app.Application
import android.content.Context

class App : Application() {

  companion object {
    lateinit var instance: App
      private set
    lateinit var ctx: Context
      private set
  }

  lateinit var history: History

  override fun onCreate() {
    super.onCreate()
    instance = this
    ctx = this.applicationContext
  }
}
