package com.chaidarun.chronofile

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_graph.*
import kotlinx.android.synthetic.main.fragment_area.*
import kotlinx.android.synthetic.main.fragment_pie.*

class GraphActivity : BaseActivity() {

  private enum class PresetRange { ALL_TIME, LAST_MONTH, LAST_WEEK, TODAY }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_graph)
    setSupportActionBar(graphToolbar)
    graphViewPager.adapter = GraphPagerAdapter(supportFragmentManager)
    graphTabs.setupWithViewPager(graphViewPager)

    // Set tab font
    // https://stackoverflow.com/a/31067431
    with(graphTabs.getChildAt(0) as ViewGroup) {
      val tabsCount = childCount
      for (i in 0 until tabsCount) {
        with(getChildAt(i) as ViewGroup) {
          val tabChildsCount = childCount
          (0 until tabChildsCount)
            .map { getChildAt(it) }
            .forEach { (it as? TextView)?.typeface = App.instance.typeface }
        }
      }
    }

    var startTime: Long? = null
    var endTime: Long? = null
    setPresetRange(Store.state.history!!, PresetRange.LAST_MONTH)
    disposables = CompositeDisposable().apply {
      add(
        Store.observable
          .map { it.graphConfig.startTime }
          .distinctUntilChanged()
          .subscribe {
            startTime = it
            if (it != null) startDate.text = formatDate(it)
          }
      )
      add(
        Store.observable
          .map { it.graphConfig.endTime }
          .distinctUntilChanged()
          .subscribe {
            endTime = it
            if (it != null) endDate.text = formatDate(it)
          }
      )
      add(
        RxView.clicks(startDate).subscribe {
          val fragment = DatePickerFragment().apply {
            arguments = Bundle().apply {
              putString(DatePickerFragment.ENDPOINT, "start")
              putLong(DatePickerFragment.TIMESTAMP, startTime ?: epochSeconds())
            }
          }
          fragment.show(fragmentManager, "datePicker")
        }
      )
      add(
        RxView.clicks(endDate).subscribe {
          val fragment = DatePickerFragment().apply {
            arguments = Bundle().apply {
              putString(DatePickerFragment.ENDPOINT, "end")
              putLong(DatePickerFragment.TIMESTAMP, endTime ?: epochSeconds())
            }
          }
          fragment.show(fragmentManager, "datePicker")
        }
      )
      add(
        RxView.clicks(quickRange).subscribe {
          with(AlertDialog.Builder(this@GraphActivity, R.style.MyAlertDialogTheme)) {
            val options = arrayOf("Today", "Past week", "Past month", "All time")
            setSingleChoiceItems(options, -1, null)
            setPositiveButton("OK") { dialog, _ ->
              when ((dialog as AlertDialog).listView.checkedItemPosition) {
                0 -> setPresetRange(Store.state.history!!, PresetRange.TODAY)
                1 -> setPresetRange(Store.state.history!!, PresetRange.LAST_WEEK)
                2 -> setPresetRange(Store.state.history!!, PresetRange.LAST_MONTH)
                3 -> setPresetRange(Store.state.history!!, PresetRange.ALL_TIME)
              }
            }
            setNegativeButton("Cancel", null)
            show()
          }
        }
      )
    }
  }

  private fun setPresetRange(history: History, presetRange: PresetRange) {
    Log.i(TAG, "Setting range to $presetRange")
    val now = history.currentActivityStartTime
    val startTime = now - when (presetRange) {
      PresetRange.ALL_TIME -> now
      PresetRange.LAST_MONTH -> 30 * DAY_SECONDS
      PresetRange.LAST_WEEK -> 7 * DAY_SECONDS
      PresetRange.TODAY -> DAY_SECONDS
    }
    Store.dispatch(Action.SetGraphRangeStart(Math.max(startTime, history.entries[0].startTime)))
    Store.dispatch(Action.SetGraphRangeEnd(now))
  }

  fun onCheckboxClicked(view: View) {
    with(view as CheckBox) {
      when (id) {
        R.id.areaIsGrouped -> Store.dispatch(Action.SetGraphGrouping(areaIsGrouped.isChecked))
        R.id.areaIsStacked -> Store.dispatch(Action.SetGraphStacking(areaIsStacked.isChecked))
        R.id.includeSleep -> Store.dispatch(Action.SetGraphIncludeSleep(includeSleep.isChecked))
        R.id.pieIsGrouped -> Store.dispatch(Action.SetGraphGrouping(pieIsGrouped.isChecked))
      }
    }
  }

  fun onRadioButtonClicked(view: View) {
    with(view as RadioButton) {
      if (!isChecked) return
      when (id) {
        R.id.radioAverage -> Store.dispatch(Action.SetGraphMetric(Metric.AVERAGE))
        R.id.radioPercentage -> Store.dispatch(Action.SetGraphMetric(Metric.PERCENTAGE))
        R.id.radioTotal -> Store.dispatch(Action.SetGraphMetric(Metric.TOTAL))
      }
    }
  }
}
