package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_area.*
import java.util.*


class AreaFragment : GraphFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_area, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    with(areaChart) {
      with(axisLeft) {
        axisMinimum = 0f
        axisMaximum = DAY_SECONDS.toFloat()
        isEnabled = false
      }
      axisRight.isEnabled = false
      description.isEnabled = false
      isScaleYEnabled = false
      with(legend) {
        isWordWrapEnabled = true
        textColor = Color.WHITE
        typeface = App.instance.typeface
      }
      onChartGestureListener = object : OnChartGestureListener {
        override fun onChartGestureEnd(
          me: MotionEvent?,
          lastPerformedGesture: ChartTouchListener.ChartGesture?
        ) {
          Store.dispatch(Action.SetGraphRangeStart(getPreviousMidnight(lowestVisibleX)))
          Store.dispatch(Action.SetGraphRangeEnd(getPreviousMidnight(highestVisibleX)))
        }

        override fun onChartFling(
          me1: MotionEvent?,
          me2: MotionEvent?,
          velocityX: Float,
          velocityY: Float
        ) = Unit

        override fun onChartSingleTapped(me: MotionEvent?) = Unit

        override fun onChartGestureStart(
          me: MotionEvent?,
          lastPerformedGesture: ChartTouchListener.ChartGesture?
        ) = Unit

        override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) = Unit
        override fun onChartLongPressed(me: MotionEvent?) = Unit
        override fun onChartDoubleTapped(me: MotionEvent?) = Unit
        override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) = Unit
      }

      setDrawBorders(false)
      setDrawGridBackground(false)
      with(xAxis) {
        setDrawAxisLine(false)
        setDrawGridLines(false)
        setDrawLabels(false)
      }
    }

    disposables = CompositeDisposable().apply {
      add(Store.observable
        .filter { it.config != null && it.history != null }
        .map { Triple(it.config!!, it.history!!, it.graphConfig) }
        .distinctUntilChanged()
        .subscribe {
          render(it)
          val visibleStart = it.third.startTime?.toFloat()
          if (visibleStart != null) areaChart.moveViewToX(visibleStart)
        }
      )
      add(Store.observable
        .map { it.graphConfig.grouped }
        .distinctUntilChanged()
        .subscribe { areaIsGrouped.isChecked = it }
      )
    }
  }

  private fun render(state: Triple<Config, History, GraphConfig>) {
    val start = System.currentTimeMillis()
    val (config, history, graphConfig) = state
    val rangeStart = history.entries[0].startTime
    val rangeEnd = history.currentActivityStartTime

    // Get top groups
    val sliceList = getSliceList(config, history, graphConfig, rangeStart, rangeEnd).first
    val groups = sliceList.map { it.first }

    // Calculate time per activity per day
    var lastSeenStartTime = history.currentActivityStartTime
    val dateToActivityToDuration = mutableMapOf<String, MutableMap<String, Long>>()
    val add: (String, String, Long) -> Unit = { date, activity, duration ->
      val aToD = dateToActivityToDuration.getOrPut(date, { mutableMapOf() })
      val slice = if (activity in groups) activity else "Other"
      aToD[slice] = aToD.getOrDefault(slice, 0) + duration
    }
    val grouped = graphConfig.grouped
    history.entries.reversed().forEach {
      val formattedStart = formatDate(it.startTime)
      val formattedEnd = formatDate(lastSeenStartTime)
      val entryCrossesMidnight = formattedStart != formattedEnd
      val slice = if (grouped) config.getActivityGroup(it.activity) else it.activity
      if (entryCrossesMidnight) {
        val midnight = with(Calendar.getInstance()) {
          apply { timeInMillis = lastSeenStartTime * 1000 }
          GregorianCalendar(
            get(Calendar.YEAR),
            get(Calendar.MONTH),
            get(Calendar.DAY_OF_MONTH)
          ).time.time / 1000
        }
        add(formatDate(midnight), slice, lastSeenStartTime - midnight)
        add(formattedStart, slice, midnight - it.startTime)
      } else {
        add(formattedStart, slice, lastSeenStartTime - it.startTime)
      }

      lastSeenStartTime = it.startTime
    }

    // Convert into data set lists
    val lines = groups.associateBy({ it }, { mutableListOf<Entry>() }).toMutableMap()
    val formattedRangeEnd = formatDate(rangeEnd)
    var dayStart = rangeStart
    val groupsReversed = groups.reversed()
    while (true) {
      val formattedDayStart = formatDate(dayStart)

      var seenSecondsToday = 0L
      for (group in groupsReversed) {
        val seconds = dateToActivityToDuration[formattedDayStart]?.get(group) ?: 0L
        seenSecondsToday += seconds
        lines[group]?.add(Entry(dayStart.toFloat(), seenSecondsToday.toFloat())) ?:
          throw Exception("$group missing from area chart data sets")
      }

      if (formattedDayStart == formattedRangeEnd) {
        break
      } else {
        dayStart += DAY_SECONDS
      }
    }

    val dataSets = groups.mapIndexed { i, group ->
      LineDataSet(lines[group], group).apply {
        val mColor = COLORS[i % COLORS.size].apply { setCircleColor(this) }
        axisDependency = YAxis.AxisDependency.LEFT
        circleRadius = 0f
        color = mColor
        cubicIntensity = 0.1f
        lineWidth = 0f
        fillAlpha = 255
        fillColor = mColor
        fillFormatter = IFillFormatter { _, _ -> areaChart.axisLeft.axisMinimum }
        mode = LineDataSet.Mode.CUBIC_BEZIER
        setDrawCircles(false)
        setDrawCircleHole(false)
        setDrawFilled(true)
        setDrawHorizontalHighlightIndicator(false)
        setDrawValues(false)
        setDrawVerticalHighlightIndicator(false)
      }
    }

    with(areaChart) {
      data = LineData(dataSets)
      invalidate()
    }

    val elapsed = System.currentTimeMillis() - start
    logDW("Rendered area chart in $elapsed ms", elapsed > 40)
  }

  /** Gets the timestamp of the last midnight that occurred before the given timestamp */
  private fun getPreviousMidnight(timestamp: Float) =
    with(Calendar.getInstance().apply { timeInMillis = timestamp.toLong() * 1000 }) {
      GregorianCalendar(
        get(Calendar.YEAR),
        get(Calendar.MONTH),
        get(Calendar.DAY_OF_MONTH)
      ).time.time / 1000
    }
}
