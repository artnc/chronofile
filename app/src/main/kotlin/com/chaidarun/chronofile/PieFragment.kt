// © Art Chaidarun

package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaidarun.chronofile.databinding.FragmentPieBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import io.reactivex.disposables.CompositeDisposable

class PieFragment : GraphFragment() {
  private var _binding: FragmentPieBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ) = FragmentPieBinding.inflate(inflater, container, false).also { _binding = it }.root

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    with(binding.pieChart) {
      description.isEnabled = false
      holeRadius = 50f
      legend.isEnabled = false
      rotationAngle = 225f
      setCenterTextColor(LABEL_COLOR)
      setCenterTextSize(LABEL_FONT_SIZE)
      setCenterTextTypeface(resources.getFont(R.font.exo2_regular))
      setDrawEntryLabels(false)
      setExtraOffsets(50f, 0f, 50f, 0f)
      setHoleColor(Color.TRANSPARENT)
      setTouchEnabled(false)
      setTransparentCircleAlpha(0)
    }

    // Populate form with current state
    with(Store.state) {
      when (graphConfig.metric) {
        Metric.AVERAGE -> binding.radioAverage
        Metric.TOTAL -> binding.radioTotal
      }.isChecked = true
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
            .subscribe { binding.pieIsGrouped.isChecked = it }
        )
      }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  /** (Re-)renders pie chart */
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
    val (_, sliceList) =
      aggregateEntries(config, history, graphConfig, rangeStart, rangeEnd, Aggregation.TOTAL)
    val pieEntries = sliceList.map { (key, value) -> PieEntry(value.toFloat(), key) }
    val metric = graphConfig.metric
    val pieDataSet =
      PieDataSet(pieEntries, "Time").apply {
        colors = COLORS
        valueLineColor = Color.TRANSPARENT
        valueLinePart1Length = 0.45f
        valueLinePart2Length = 0f
        valueTextColor = LABEL_COLOR
        valueTextSize = LABEL_FONT_SIZE
        valueTypeface = resources.getFont(R.font.exo2_regular)
        valueFormatter =
          object : ValueFormatter() {
            override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
              val num: String =
                when (metric) {
                  Metric.AVERAGE -> formatDuration(value.toLong() * DAY_SECONDS / rangeSeconds)
                  Metric.TOTAL -> formatDuration(value.toLong())
                }
              return "${pieEntry?.label}: $num"
            }
          }
        yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
      }

    with(binding.pieChart) {
      centerText = "Range:\n${formatDuration(sliceList.map { it.second }.sum(), showDays = true)}"
      data = PieData(pieDataSet)
      invalidate()
    }

    val elapsed = System.currentTimeMillis() - start
    Log.i(TAG, "Rendered pie chart in $elapsed ms")
  }
}
