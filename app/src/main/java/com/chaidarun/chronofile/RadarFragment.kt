package com.chaidarun.chronofile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import io.reactivex.disposables.CompositeDisposable
import java.text.DateFormatSymbols
import java.util.*
import kotlinx.android.synthetic.main.fragment_radar.*

class RadarFragment : GraphFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_radar, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    radarChart.run {
      description.isEnabled = false
      legend.run {
        isWordWrapEnabled = true
        textColor = LABEL_COLOR
        textSize = LABEL_FONT_SIZE
        typeface = App.instance.typeface
        xEntrySpace = 15f
      }
      setTouchEnabled(false)
      xAxis.run {
        setValueFormatter { value, _ ->
          DateFormatSymbols(Locale.US).shortWeekdays[((value.toInt() + 1) % 7) + 1]
        }
        textColor = LABEL_COLOR
        textSize = LABEL_FONT_SIZE
        typeface = App.instance.typeface
      }
      yAxis.run {
        axisMinimum = 0f
        setDrawLabels(false)
      }
    }

    disposables = CompositeDisposable().apply {
      add(
        Store.observable
          .filter { it.config != null && it.history != null }
          .map { Triple(it.config!!, it.history!!, it.graphConfig) }
          .distinctUntilChanged()
          .subscribe { render(it) }
      )
      add(
        Store.observable
          .map { it.graphConfig.grouped }
          .distinctUntilChanged()
          .subscribe { radarIsGrouped.isChecked = it }
      )
    }
  }

  /** (Re-)renders radar chart */
  private fun render(state: Triple<Config, History, GraphConfig>) {
    val start = System.currentTimeMillis()
    val (config, history, graphConfig) = state

    // Determine date range
    val (rangeStart, rangeEnd) = getChartRange(history, graphConfig)
    val rangeSeconds = rangeEnd - rangeStart
    if (rangeSeconds <= 0) {
      App.toast("No data to show!")
      return
    }

    // Show data
    val (buckets, sliceList) =
      aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.DAY_OF_WEEK)
    var maxEntrySeconds = 0L
    val radarDataSets = sliceList.mapIndexed { i, (slice, _) ->
      val radarEntries = (1L until 8L).map { dayOfWeek ->
        val seconds = buckets.getOrDefault(dayOfWeek, emptyMap()).getOrDefault(slice, 0)
        maxEntrySeconds = Math.max(maxEntrySeconds, seconds)
        RadarEntry(seconds.toFloat())
      }
      RadarDataSet(radarEntries, slice).apply {
        color = COLORS[i]
        setDrawValues(false)
      }
    }
    radarChart.run {
      data = RadarData(radarDataSets)
      invalidate()
    }

    val elapsed = System.currentTimeMillis() - start
    Log.i(TAG, "Rendered radar chart in $elapsed ms")
  }
}
