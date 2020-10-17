package com.chaidarun.chronofile

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable

abstract class BaseFragment : Fragment() {

  /**
   * RxJava subscriptions that should be GC'ed once this activity exits
   *
   * https://medium.com/@vanniktech/rxjava-2-disposable-under-the-hood-f842d2373e64
   */
  protected var disposables: CompositeDisposable? = null

  override fun onAttach(context: Context) {
    logLifecycleEvent("onAttach")
    super.onAttach(context)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    logLifecycleEvent("onCreate")
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    logLifecycleEvent("onCreateView")
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    logLifecycleEvent("onActivityCreated()")
    super.onActivityCreated(savedInstanceState)
  }

  override fun onDestroyView() {
    logLifecycleEvent("onDestroyView")
    super.onDestroyView()
  }

  override fun onDestroy() {
    logLifecycleEvent("onDestroy")

    // Clean up Rx subscriptions
    disposables?.clear()

    super.onDestroy()
  }

  override fun onDetach() {
    logLifecycleEvent("onDetach")
    super.onDetach()
  }

  private fun logLifecycleEvent(event: String) {
    Log.i(TAG, "${this.javaClass.simpleName} $event")
  }
}
