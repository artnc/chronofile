package com.chaidarun.chronofile

import android.app.Application

class App : Application() {

  companion object {
    lateinit var instance: App
      private set
  }

  lateinit var history: History

  override fun onCreate() {
    super.onCreate()
    instance = this
  }
}
