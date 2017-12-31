package com.chaidarun.chronofile

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import io.reactivex.disposables.Disposable
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

abstract class BaseActivity : AppCompatActivity() {

  /** RxJava subscriptions that should be GC'ed once this activity exits */
  protected var disposables: List<Disposable>? = null

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
    disposables?.forEach {
      if (!it.isDisposed) {
        Log.d(TAG, "Disposing of $it")
        it.dispose()
      }
    }

    super.onDestroy()
  }

  override fun attachBaseContext(newBase: Context) {
    // Use global default font
    super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
  }

  private fun logLifecycleEvent(event: String) {
    Log.d(TAG, "${this.javaClass.simpleName} $event")
  }
}
