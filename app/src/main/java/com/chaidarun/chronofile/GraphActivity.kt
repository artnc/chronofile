package com.chaidarun.chronofile

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.chaidarun.chronofile.databinding.ActivityGraphBinding
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable

class GraphActivity : BaseActivity() {
  private val binding by viewBinding(ActivityGraphBinding::inflate)

  private enum class PresetRange(val text: String, val duration: Long) {
    TODAY("Today", DAY_SECONDS),
    PAST_WEEK("Past week", 7 * DAY_SECONDS),
    PAST_MONTH("Past month", 30 * DAY_SECONDS),
    ALL_TIME("All time", Long.MAX_VALUE)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSupportActionBar(binding.graphToolbar)
    binding.graphViewPager.run {
      adapter = GraphPagerAdapter(supportFragmentManager)
      currentItem = GraphPagerAdapter.Tab.PIE.ordinal
      offscreenPageLimit = GraphPagerAdapter.Tab.entries.size
    }
    binding.graphTabs.setupWithViewPager(binding.graphViewPager)

    // Set tab font
    // https://stackoverflow.com/a/31067431
    with(binding.graphTabs.getChildAt(0) as ViewGroup) {
      val tabsCount = childCount
      for (i in 0 until tabsCount) {
        with(getChildAt(i) as ViewGroup) {
          val tabChildsCount = childCount
          (0 until tabChildsCount)
            .map { getChildAt(it) }
            .forEach { (it as? TextView)?.typeface = resources.getFont(R.font.exo2_regular) }
        }
      }
    }

    var startTime: Long? = null
    var endTime: Long? = null
    setPresetRange(Store.state.history!!, PresetRange.PAST_MONTH)
    disposables =
      CompositeDisposable().apply {
        add(
          Store.observable
            .map { it.graphConfig.startTime }
            .distinctUntilChanged()
            .subscribe {
              startTime = it
              if (it != null) binding.startDate.text = formatDate(it)
            }
        )
        add(
          Store.observable
            .map { it.graphConfig.endTime }
            .distinctUntilChanged()
            .subscribe {
              endTime = it
              if (it != null) binding.endDate.text = formatDate(it)
            }
        )
        add(
          RxView.clicks(binding.startDate).subscribe {
            DatePickerFragment()
              .apply {
                arguments =
                  Bundle().apply {
                    putString(DatePickerFragment.ENDPOINT, "start")
                    putLong(DatePickerFragment.TIMESTAMP, startTime ?: epochSeconds())
                  }
              }
              .show(supportFragmentManager, "datePicker")
          }
        )
        add(
          RxView.clicks(binding.endDate).subscribe {
            DatePickerFragment()
              .apply {
                arguments =
                  Bundle().apply {
                    putString(DatePickerFragment.ENDPOINT, "end")
                    putLong(DatePickerFragment.TIMESTAMP, endTime ?: epochSeconds())
                  }
              }
              .show(supportFragmentManager, "datePicker")
          }
        )
        add(
          RxView.clicks(binding.quickRange).subscribe {
            with(AlertDialog.Builder(this@GraphActivity, R.style.MyAlertDialogTheme)) {
              setSingleChoiceItems(PresetRange.entries.map { it.text }.toTypedArray(), -1, null)
              setPositiveButton("OK") { dialog, _ ->
                val position = (dialog as AlertDialog).listView.checkedItemPosition
                setPresetRange(Store.state.history!!, PresetRange.entries[position])
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
    val startTime =
      Math.max(now - presetRange.duration, history.entries.getOrNull(0)?.startTime ?: 0)
    Store.dispatch(Action.SetGraphRangeStart(startTime))
    Store.dispatch(Action.SetGraphRangeEnd(now))
  }

  fun onCheckboxClicked(view: View) {
    with(view as CheckBox) {
      when (id) {
        R.id.areaIsGrouped -> Store.dispatch(Action.SetGraphGrouping(isChecked))
        R.id.areaIsStacked -> Store.dispatch(Action.SetGraphStacking(isChecked))
        R.id.pieIsGrouped -> Store.dispatch(Action.SetGraphGrouping(isChecked))
        R.id.radarIsGrouped -> Store.dispatch(Action.SetGraphGrouping(isChecked))
      }
    }
  }

  fun onRadioButtonClicked(view: View) {
    with(view as RadioButton) {
      if (!isChecked) return
      when (id) {
        R.id.radioAverage -> Store.dispatch(Action.SetGraphMetric(Metric.AVERAGE))
        R.id.radioTotal -> Store.dispatch(Action.SetGraphMetric(Metric.TOTAL))
      }
    }
  }
}
