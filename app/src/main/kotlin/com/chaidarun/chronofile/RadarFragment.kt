// © Art Chaidarun

package com.chaidarun.chronofile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaidarun.chronofile.databinding.FragmentRadarBinding
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import io.reactivex.disposables.CompositeDisposable
import java.text.DateFormatSymbols
import java.util.Locale

class RadarFragment : GraphFragment() {
  private var _binding: FragmentRadarBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ) = FragmentRadarBinding.inflate(inflater, container, false).also { _binding = it }.root

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.radarChart.run {
      description.isEnabled = false
      legend.run {
        isWordWrapEnabled = true
        textColor = LABEL_COLOR
        textSize = LABEL_FONT_SIZE
        typeface = resources.getFont(R.font.exo2_regular)
        xEntrySpace = 15f
      }
      setDrawWeb(false)
      setTouchEnabled(false)
      xAxis.run {
        valueFormatter =
          object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?) =
              DateFormatSymbols(Locale.US).shortWeekdays[((value.toInt() + 1) % 7) + 1]
          }
        textColor = LABEL_COLOR
        textSize = LABEL_FONT_SIZE
        typeface = resources.getFont(R.font.exo2_regular)
      }
      yAxis.run {
        axisMinimum = 0f
        setDrawLabels(false)
      }
    }

    disposables =
      CompositeDisposable().apply {
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
            .subscribe { binding.radarIsGrouped.isChecked = it }
        )
      }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
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
    val radarDataSets =
      sliceList.mapIndexed { i, (slice, _) ->
        val radarEntries =
          (1L until 8L).map { dayOfWeek ->
            val seconds = buckets.getOrDefault(dayOfWeek, emptyMap()).getOrDefault(slice, 0)
            maxEntrySeconds = Math.max(maxEntrySeconds, seconds)
            RadarEntry(Math.sqrt(seconds.toDouble()).toFloat())
          }
        RadarDataSet(radarEntries, slice).apply {
          color = COLORS[i % COLORS.size]
          setDrawValues(false)
        }
      }
    binding.radarChart.run {
      data = RadarData(radarDataSets)
      invalidate()
    }

    val elapsed = System.currentTimeMillis() - start
    Log.i(TAG, "Rendered radar chart in $elapsed ms")
  }
}
