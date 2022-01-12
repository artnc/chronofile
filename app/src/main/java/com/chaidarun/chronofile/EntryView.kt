package com.chaidarun.chronofile

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import android.widget.LinearLayout

class EntryView : LinearLayout, Checkable {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

  override fun setChecked(checked: Boolean) {
    isActivated = checked;
  }

  override fun isChecked(): Boolean {
    return isActivated;
  }

  override fun toggle() {
    isActivated = !isActivated;
  }
}
