// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

private enum class PresetRange(val text: String, val duration: Long) {
  TODAY("Today", DAY_SECONDS),
  PAST_WEEK("Past week", 7 * DAY_SECONDS),
  PAST_MONTH("Past month", 30 * DAY_SECONDS),
  PAST_YEAR("Past year", 365 * DAY_SECONDS),
  ALL_TIME("All time", Long.MAX_VALUE),
}

private enum class ChartTab(val title: String) {
  MATRIX("Matrix"),
  RADAR("Radar"),
  PIE("Pie"),
  AREA("Area"),
  COUNT("Count"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: MainViewModel, onNavigateUp: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val config = state.config
  val history = state.history
  val chartConfig = state.chartConfig
  var showPresetDialog by remember { mutableStateOf(false) }
  // Which date-range endpoint the picker is editing (true = start, false = end, null = closed)
  var datePickerForStart by remember { mutableStateOf<Boolean?>(null) }

  // Apply the default preset range the first time history becomes available; on cold start the
  // hydrate runs async and history is null when this screen first composes.
  LaunchedEffect(history) {
    if (history != null && chartConfig.startTime == null && chartConfig.endTime == null) {
      setPresetRange(viewModel, history, PresetRange.PAST_MONTH)
    }
  }

  AppScaffold(title = "Analytics", onNavigateUp = onNavigateUp) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      val pagerState =
        rememberPagerState(initialPage = ChartTab.PIE.ordinal) { ChartTab.entries.size }
      val scope = rememberCoroutineScope()
      PrimaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = ColorPrimary,
        contentColor = Color.White,
        // Drop the default divider, which renders as a gray line (unset outlineVariant) below tabs
        divider = {},
      ) {
        ChartTab.entries.forEachIndexed { index, tab ->
          Tab(
            selected = pagerState.currentPage == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = { Text(tab.title) },
            selectedContentColor = Color.White,
            unselectedContentColor = Color.White.copy(alpha = 0.6f),
          )
        }
      }
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
        when (ChartTab.entries[page]) {
          ChartTab.MATRIX -> MatrixChart(viewModel, config, history, chartConfig)
          ChartTab.PIE -> PieChart(viewModel, config, history, chartConfig)
          ChartTab.AREA -> AreaChart(viewModel, config, history, chartConfig)
          ChartTab.COUNT -> CountChart(viewModel, config, history, chartConfig)
          ChartTab.RADAR -> RadarChart(viewModel, config, history, chartConfig)
        }
      }
      DateRangeBar(
        chartConfig = chartConfig,
        onPickStart = { datePickerForStart = true },
        onPickEnd = { datePickerForStart = false },
        onPickPreset = { showPresetDialog = true },
      )
    }
  }

  if (showPresetDialog) {
    PresetRangeDialog(
      onPick = { range ->
        history?.let { setPresetRange(viewModel, it, range) }
        showPresetDialog = false
      },
      onDismiss = { showPresetDialog = false },
    )
  }

  datePickerForStart?.let { isStart ->
    ChartDatePickerDialog(
      initialSeconds =
        (if (isStart) chartConfig.startTime else chartConfig.endTime) ?: epochSeconds(),
      onPick = { picked ->
        if (isStart) viewModel.dispatch(Action.SetChartRangeStart(picked))
        else viewModel.dispatch(Action.SetChartRangeEnd(picked))
        datePickerForStart = null
      },
      onDismiss = { datePickerForStart = null },
    )
  }
}

@Composable
private fun ChartDatePickerDialog(
  initialSeconds: Long,
  onPick: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  // The Material3 picker speaks UTC-midnight millis; translate to/from the local calendar date so a
  // picked day maps to that day's local midnight (matching the old GregorianCalendar behavior)
  val initialMillis =
    remember(initialSeconds) {
      getDate(initialSeconds).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
  val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          pickerState.selectedDateMillis?.let { millis ->
            onPick(
              Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
            )
          }
        }
      ) {
        Text("OK")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  ) {
    DatePicker(state = pickerState)
  }
}

private fun setPresetRange(viewModel: MainViewModel, history: History, presetRange: PresetRange) {
  Log.i(TAG, "Setting range to $presetRange")
  val now = history.currentActivityStartTime
  val startTime = maxOf(now - presetRange.duration, history.entries.getOrNull(0)?.startTime ?: 0)
  viewModel.dispatch(Action.SetChartRangeStart(startTime))
  viewModel.dispatch(Action.SetChartRangeEnd(now))
}

@Composable
private fun DateRangeBar(
  chartConfig: ChartConfig,
  onPickStart: () -> Unit,
  onPickEnd: () -> Unit,
  onPickPreset: () -> Unit,
) {
  val buttonColors =
    ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = Color.White)
  Row(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Button(
      onClick = onPickStart,
      shape = ButtonShape,
      colors = buttonColors,
      modifier = Modifier.weight(1f),
    ) {
      Text(chartConfig.startTime?.let { formatDate(it) } ?: "—")
    }
    Text(
      "to",
      color = Color.White,
      modifier =
        Modifier.padding(horizontal = 12.dp)
          .clickable(interactionSource = null, indication = null, onClick = onPickPreset),
    )
    Button(
      onClick = onPickEnd,
      shape = ButtonShape,
      colors = buttonColors,
      modifier = Modifier.weight(1f),
    ) {
      Text(chartConfig.endTime?.let { formatDate(it) } ?: "—")
    }
  }
}

@Composable
private fun PresetRangeDialog(onPick: (PresetRange) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quick range") },
    text = {
      Column {
        PresetRange.entries.forEach { range ->
          // Tapping a range applies it immediately (no separate confirm step)
          Text(
            range.text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable { onPick(range) }.padding(vertical = 12.dp),
          )
        }
      }
    },
    // No confirm button: tapping a range commits immediately
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    containerColor = ColorPrimaryDark,
  )
}
