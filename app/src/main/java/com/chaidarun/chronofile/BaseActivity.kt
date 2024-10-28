package com.chaidarun.chronofile

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.disposables.CompositeDisposable

abstract class BaseActivity : AppCompatActivity() {

  /**
   * RxJava subscriptions that should be GC'ed once this activity exits
   *
   * https://medium.com/@vanniktech/rxjava-2-disposable-under-the-hood-f842d2373e64
   */
  protected var disposables: CompositeDisposable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    logLifecycleEvent("onCreate")
    super.onCreate(savedInstanceState)

    // Keep screen awake
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun onStart() {
    logLifecycleEvent("onStart")
    super.onStart()
  }

  override fun onResume() {
    logLifecycleEvent("onResume")
    super.onResume()
  }

  override fun onPause() {
    logLifecycleEvent("onPause")
    super.onPause()
  }

  override fun onStop() {
    logLifecycleEvent("onStop")
    super.onStop()
  }

  override fun onRestart() {
    logLifecycleEvent("onRestart")
    super.onRestart()
  }

  override fun onDestroy() {
    logLifecycleEvent("onDestroy")

    // Clean up Rx subscriptions
    disposables?.clear()

    super.onDestroy()
  }

  private fun logLifecycleEvent(event: String) {
    Log.i(TAG, "${this.javaClass.simpleName} $event")
  }
}
