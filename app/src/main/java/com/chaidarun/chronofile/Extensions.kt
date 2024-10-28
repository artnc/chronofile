package com.chaidarun.chronofile

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

/**
 * https://zhuinden.medium.com/simple-one-liner-viewbinding-in-fragments-and-activities-with-kotlin-961430c6c07c
 * https://github.com/duolingo/literacy-android/blob/43522a3f93b7846ca5900cab508506650d450097/shared/src/main/java/com/duolingo/shared/extensions/AppCompatActivity.kt#L11-L17
 */
inline fun <T : ViewBinding> AppCompatActivity.viewBinding(
  crossinline bindingInflater: (LayoutInflater) -> T
) =
  lazy(LazyThreadSafetyMode.NONE) {
    bindingInflater.invoke(layoutInflater).also { binding -> setContentView(binding.root) }
  }
