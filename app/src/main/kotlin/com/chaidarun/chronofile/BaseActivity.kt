// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import io.reactivex.disposables.CompositeDisposable

abstract class BaseActivity : AppCompatActivity() {

  /**
   * RxJava subscriptions that should be GC'ed once this activity exits
   *
   * https://medium.com/@vanniktech/rxjava-2-disposable-under-the-hood-f842d2373e64
   */
  protected var disposables: CompositeDisposable? = null

  /**
   * https://zhuinden.medium.com/simple-one-liner-viewbinding-in-fragments-and-activities-with-kotlin-961430c6c07c
   * https://github.com/duolingo/literacy-android/blob/43522a3f93b7846ca5900cab508506650d450097/shared/src/main/java/com/duolingo/shared/extensions/AppCompatActivity.kt#L11-L17
   */
  inline fun <T : ViewBinding> viewBinding(crossinline bindingInflater: (LayoutInflater) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
      bindingInflater.invoke(layoutInflater).also { setContentView(it.root) }
    }

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

  override fun onNewIntent(intent: Intent?) {
    logLifecycleEvent("onNewIntent")
    super.onNewIntent(intent)
  }

  private fun logLifecycleEvent(event: String) {
    Log.d(TAG, "${this.javaClass.simpleName} $event")
  }
}
