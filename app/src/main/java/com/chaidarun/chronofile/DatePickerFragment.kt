package com.chaidarun.chronofile

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.widget.DatePicker
import java.util.*

class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val timestamp = arguments.getLong(TIMESTAMP)
    val c = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
    val year = c.get(Calendar.YEAR)
    val month = c.get(Calendar.MONTH)
    val day = c.get(Calendar.DAY_OF_MONTH)
    return DatePickerDialog(activity, this, year, month, day)
  }

  override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
    val timestamp = GregorianCalendar(year, month, day).time.time / 1000
    when (arguments.getString(ENDPOINT)) {
      "end" -> Store.dispatch(Action.SetGraphRangeEnd(timestamp))
      "start" -> Store.dispatch(Action.SetGraphRangeStart(timestamp))
      else -> throw Error("Invalid argument for DatePickerFragment")
    }
  }

  companion object {
    val ENDPOINT = "endpoint"
    val TIMESTAMP = "timestamp"
  }
}
