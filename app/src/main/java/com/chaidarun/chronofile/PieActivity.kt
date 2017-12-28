package com.chaidarun.chronofile

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.android.synthetic.main.activity_pie.*
import org.jetbrains.anko.collections.forEachReversedByIndex

class PieActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_pie)

    pieChart.legend.form = Legend.LegendForm.CIRCLE
    pieChart.legend.position = Legend.LegendPosition.LEFT_OF_CHART
    pieChart.setTransparentCircleAlpha(0)
    pieChart.setDrawEntryLabels(true)
    setData()
  }

  private fun setData() {
    // Get data
    val seconds = mutableMapOf<String, Long>()
    with(App.instance.history) {
      var endTime = currentActivityStartTime
      entries.forEachReversedByIndex {
        seconds[it.activity] = seconds.getOrDefault(it.activity, 0) + endTime - it.startTime
        endTime = it.startTime
      }
    }
    val pieEntries = seconds.entries.sortedByDescending { it.value }.map { PieEntry(it.value.toFloat(), it.key) }

    // Show data
    val pieDataSet = PieDataSet(pieEntries, "Time")
    pieDataSet.colors = ColorTemplate.VORDIPLOM_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList()
    pieDataSet.valueTextSize = 12f
    pieChart.data = PieData(pieDataSet)
    pieChart.invalidate()
  }
}
