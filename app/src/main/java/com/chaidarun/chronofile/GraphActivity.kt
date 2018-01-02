package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.RadioButton
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_graph.*

private enum class PresetRange { ALL_TIME, LAST_MONTH, LAST_WEEK }
enum class Metric { AVERAGE, PERCENTAGE, TOTAL }
data class GraphSettings(
  val grouped: Boolean = true,
  val metric: Metric = Metric.AVERAGE,
  val startTime: Long? = null,
  val endTime: Long? = null
)

class GraphActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_graph)
    setSupportActionBar(graphToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    setPresetRange(Store.state.value.history!!, PresetRange.LAST_MONTH)

    graphViewPager.adapter = GraphPagerAdapter(supportFragmentManager)
    graphTabs.setupWithViewPager(graphViewPager)

    var startTime: Long? = null
    var endTime: Long? = null
    disposables = CompositeDisposable().apply {
      add(Store.state
        .map { it.graphSettings.startTime }
        .distinctUntilChanged()
        .subscribe {
          startTime = it
          if (it != null) startDate.text = formatDate(it)
        }
      )
      add(Store.state
        .map { it.graphSettings.endTime }
        .distinctUntilChanged()
        .subscribe {
          endTime = it
          if (it != null) endDate.text = formatDate(it)
        }
      )
      add(RxView.clicks(startDate).subscribe {
        val fragment = DatePickerFragment().apply {
          arguments = Bundle().apply {
            putString(DatePickerFragment.ENDPOINT, "start")
            putLong(DatePickerFragment.TIMESTAMP, startTime ?: epochSeconds())
          }
        }
        fragment.show(fragmentManager, "datePicker")
      })
      add(RxView.clicks(endDate).subscribe {
        val fragment = DatePickerFragment().apply {
          arguments = Bundle().apply {
            putString(DatePickerFragment.ENDPOINT, "end")
            putLong(DatePickerFragment.TIMESTAMP, endTime ?: epochSeconds())
          }
        }
        fragment.show(fragmentManager, "datePicker")
      })
      add(RxView.clicks(quickRange).subscribe {
        with(AlertDialog.Builder(this@GraphActivity, R.style.MyAlertDialogTheme)) {
          val options = arrayOf("Past week", "Past month", "All time")
          setSingleChoiceItems(options, 0, null)
          setPositiveButton("OK") { dialog, _ ->
            val optionIndex = (dialog as AlertDialog).listView.checkedItemPosition
            setPresetRange(Store.state.value.history!!, when (optionIndex) {
              0 -> PresetRange.LAST_WEEK
              1 -> PresetRange.LAST_MONTH
              2 -> PresetRange.ALL_TIME
              else -> throw Exception("Invalid preset range")
            })
          }
          setNegativeButton("Cancel", null)
          show()
        }
      })
    }
  }

  private fun setPresetRange(history: History, presetRange: PresetRange) {
    Log.d(TAG, "Setting range to $presetRange")
    val now = history.currentActivityStartTime
    val startTime = now - when (presetRange) {
      PresetRange.ALL_TIME -> now
      PresetRange.LAST_MONTH -> now - 30 * DAY_SECONDS
      PresetRange.LAST_WEEK -> now - 7 * DAY_SECONDS
    }
    Store.dispatch(Action.SetGraphRangeStart(Math.max(startTime, history.entries[0].startTime)))
    Store.dispatch(Action.SetGraphRangeEnd(now))
  }

  fun onRadioButtonClicked(view: View) {
    with(view as RadioButton) {
      if (!isChecked) {
        return
      }
      when (id) {
        R.id.radioAverage -> Store.dispatch(Action.SetGraphMetric(Metric.AVERAGE))
        R.id.radioGrouped -> Store.dispatch(Action.SetGraphGrouping(true))
        R.id.radioIndividual -> Store.dispatch(Action.SetGraphGrouping(false))
        R.id.radioPercentage -> Store.dispatch(Action.SetGraphMetric(Metric.PERCENTAGE))
        R.id.radioTotal -> Store.dispatch(Action.SetGraphMetric(Metric.TOTAL))
      }
    }
  }

  companion object {
    val COLORS by lazy {
      listOf(
        "#66BB6A",
        "#388E3C",
        "#81C784",
        "#4CAF50",
        "#2E7D32",
        "#1B5E20",
        "#A5D6A7",
        "#43A047"
      ).map { Color.parseColor(it) }
    }
  }
}
