// © Art Chaidarun

package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date
import java.util.Locale
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val MAX_ENTRIES_TO_SHOW = 1000

private val APP_PERMISSIONS =
  arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

private sealed interface TimelineItem {
  data class DateItem(val text: String) : TimelineItem

  data class EntryItem(val entry: Entry, val duration: String) : TimelineItem

  data class SpacerItem(val height: Int) : TimelineItem

  data class TimeItem(val text: String) : TimelineItem
}

private data class TimelineBuild(
  val items: List<TimelineItem>,
  val matchCount: Int,
  val matchSeconds: Long,
)

private fun buildTimeline(history: History, query: String?): TimelineBuild {
  var matchCount = 0
  var matchSeconds = 0L
  val entriesToShow = mutableListOf<Pair<Entry, Long>>()
  var lastSeenStartTime = history.currentActivityStartTime
  // Hoisted out of the per-entry loop since they don't depend on the entry
  val queryLower = query?.lowercase()
  val queryIsNumeric = query?.toIntOrNull() != null
  for (entry in history.entries.reversed()) {
    if (
      query == null ||
        queryLower!! in "${entry.activity}|${entry.note ?: ""}".lowercase() ||
        queryIsNumeric && formatForSearch(entry.startTime).startsWith(query)
    ) {
      matchSeconds += lastSeenStartTime - entry.startTime
      if (++matchCount <= MAX_ENTRIES_TO_SHOW) {
        entriesToShow.add(Pair(entry, lastSeenStartTime))
      }
    }
    lastSeenStartTime = entry.startTime
  }
  entriesToShow.reverse()

  val items = mutableListOf<TimelineItem>(TimelineItem.SpacerItem(8))
  var lastDateShown: String? = null
  var lastTimeShown: Long? = null
  for ((entry, endTime) in entriesToShow) {
    val startDate = formatDate(entry.startTime)
    if (startDate != lastDateShown) {
      items.add(TimelineItem.DateItem(startDate))
      lastDateShown = startDate
    }
    if (entry.startTime != lastTimeShown) {
      items.add(TimelineItem.TimeItem(formatTime(Date(entry.startTime * 1000))))
      lastTimeShown = entry.startTime
    }
    val endDate = formatDate(endTime)
    if (startDate != endDate) {
      val midnight = getPreviousMidnight(endTime)
      items.add(TimelineItem.EntryItem(entry, formatDuration(midnight - entry.startTime)))
      items.add(TimelineItem.DateItem(endDate))
      lastDateShown = endDate
      items.add(TimelineItem.EntryItem(entry, formatDuration(endTime - midnight)))
    } else {
      items.add(TimelineItem.EntryItem(entry, formatDuration(endTime - entry.startTime)))
    }
    items.add(TimelineItem.TimeItem(formatTime(Date(endTime * 1000))))
    lastTimeShown = endTime
  }
  // Trailing spacer renders at the bottom (above the add-entry bar); keep it small
  items.add(TimelineItem.SpacerItem(8))
  return TimelineBuild(items, matchCount, matchSeconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
  viewModel: MainViewModel,
  refreshTick: Int,
  onOpenSettings: () -> Unit,
  onOpenStats: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val history = state.history
  val query = state.searchQuery
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  var showSearchDialog by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  var editingEntry by remember { mutableStateOf<Entry?>(null) }

  var showStorageBanner by remember {
    mutableStateOf(
      IOUtil.getPref(IOUtil.STORAGE_DIR_PREF).isNullOrEmpty() ||
        !IOUtil.persistAndCheckStoragePermission()
    )
  }
  var showLocationBanner by remember {
    mutableStateOf(
      !APP_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
      }
    )
  }

  val openDocumentTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri == null) {
        App.toast("Storage location not changed")
      } else {
        showStorageBanner = false
        App.toast("Successfully set storage location")
        IOUtil.setPref(IOUtil.STORAGE_DIR_PREF, uri.toString())
        IOUtil.persistAndCheckStoragePermission()
        viewModel.hydrate(force = true)
      }
    }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      results ->
      if (results.values.all { it }) {
        showLocationBanner = false
        App.toast("Permissions granted successfully :)")
      } else {
        App.toast("You denied permission :(")
      }
    }

  val build =
    remember(history, query, refreshTick) {
      if (history == null) TimelineBuild(emptyList(), 0, 0L)
      else {
        val (result, elapsed) = measureTimedValue { buildTimeline(history, query) }
        Log.i(TAG, "Rendered history view in ${elapsed.inWholeMilliseconds} ms")
        result
      }
    }
  val items = build.items

  // Toast search-result counts only when the search itself or underlying history changes (not
  // on time-zone refreshTick bumps, which would re-toast on every resume)
  LaunchedEffect(history, query) {
    if (query != null && history != null) {
      App.toast(
        "${build.matchCount} results, ${formatDuration(build.matchSeconds, showDays = true, showMinutes = false)}"
      )
    }
  }

  // Scroll to the newest entry on every history change (add/edit/delete); keyed on the history
  // object, not items.size, since adding a duplicate of the last activity dedups to the same size.
  // Index 0 is the bottom under reverseLayout, which keeps it pinned through keyboard resizes.
  val listState = rememberLazyListState()
  LaunchedEffect(history) { if (history != null) listState.scrollToItem(0) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (query == null) "Timeline" else "\"$query\"") },
        actions = {
          IconButton(onClick = { showSearchDialog = true }) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
          }
          IconButton(onClick = onOpenStats) {
            Icon(Icons.Filled.Visibility, contentDescription = "Stats")
          }
          IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = ColorPrimary,
          ) {
            DropdownMenuItem(
              text = { Text("Settings") },
              onClick = {
                showMenu = false
                onOpenSettings()
              },
            )
            DropdownMenuItem(
              text = { Text("Change save directory") },
              onClick = {
                showMenu = false
                openDocumentTreeLauncher.launch(null)
              },
            )
            DropdownMenuItem(
              text = { Text("About") },
              onClick = {
                showMenu = false
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/artnc/chronofile#chronofile".toUri(),
                  )
                )
              },
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = ColorPrimary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
          ),
      )
    },
    containerColor = ColorPrimaryDark,
  ) { padding ->
    // consumeWindowInsets prevents the bottom system-bar inset (already applied via padding) from
    // being double-counted by imePadding, which otherwise leaves a gap above the keyboard
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
    ) {
      if (showStorageBanner) {
        BannerRow(
          text =
            "Choose a save directory. Otherwise your logged activities will be lost upon exit!",
          buttonText = "Go",
          onClick = { openDocumentTreeLauncher.launch(null) },
        )
      }
      if (showLocationBanner) {
        BannerRow(
          text = "Grant location permissions so we can record where your activities happen.",
          buttonText = "Go",
          onClick = { permissionLauncher.launch(APP_PERMISSIONS) },
        )
      }
      // reverseLayout pins the list to the bottom (like the old LinearLayoutManager.stackFromEnd):
      // index 0 (the trailing spacer) sits at the bottom, so newly-added entries appear there and
      // the bottom stays put when the keyboard shrinks the viewport (no manual scrolling needed)
      LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        state = listState,
        reverseLayout = true,
      ) {
        items(items.asReversed()) { item ->
          when (item) {
            is TimelineItem.DateItem -> DateRow(item)
            is TimelineItem.TimeItem -> TimeRow(item)
            is TimelineItem.SpacerItem -> Spacer(Modifier.height(item.height.dp))
            is TimelineItem.EntryItem ->
              EntryRow(
                item,
                onClick = { viewModel.addEntry(item.entry.activity, item.entry.note) },
                onEdit = { editingEntry = item.entry },
              )
          }
        }
      }
      AddEntryBar(
        onAdd = { activity, note ->
          viewModel.addEntry(activity, note)
          focusManager.clearFocus()
        }
      )
    }
  }

  if (showSearchDialog) {
    SearchDialog(
      initial = query.orEmpty(),
      onDismiss = { showSearchDialog = false },
      onSubmit = { input ->
        val q = if (input.isBlank()) null else input.trim()
        viewModel.dispatch(Action.SetSearchQuery(q))
        showSearchDialog = false
      },
      onClear = {
        viewModel.dispatch(Action.SetSearchQuery(null))
        showSearchDialog = false
      },
    )
  }

  editingEntry?.let { entry ->
    EntryEditDialog(
      entry = entry,
      onDelete = {
        viewModel.dispatch(Action.RemoveEntry(entry.startTime))
        editingEntry = null
      },
      onDismiss = { editingEntry = null },
      onConfirm = { startTime, activity, note ->
        viewModel.dispatch(Action.EditEntry(entry.startTime, startTime, activity, note))
        editingEntry = null
      },
    )
  }
}

/** In-memory cache of resolved addresses keyed by (lat, lon), reused across dialog opens */
private val geocodeCache = mutableMapOf<Pair<Double, Double>, String>()

/** Trailing zip code and country, stripped from displayed addresses only */
private val addressSuffixRegex = Regex(",? \\d{5},? USA?$")

/** Reverse-geocodes an entry's coordinates into a human-readable address, or null if unavailable */
private suspend fun geocodeEntry(entry: Entry): String? {
  val latLon = entry.latLon ?: return null
  geocodeCache[latLon]?.let {
    return it
  }
  return withContext(Dispatchers.IO) {
      try {
        @Suppress("DEPRECATION")
        Geocoder(App.ctx, Locale.US)
          .getFromLocation(latLon.first, latLon.second, 1)
          ?.firstOrNull()
          ?.getAddressLine(0)
      } catch (_: Exception) {
        null
      }
    }
    // Cache only successful lookups so transient failures get retried
    ?.also { geocodeCache[latLon] = it }
}

@Composable
private fun BannerRow(text: String, buttonText: String, onClick: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
    Text(text, modifier = Modifier.weight(1f).padding(end = 16.dp), color = Color.White)
    Button(
      onClick = onClick,
      shape = ButtonShape,
      colors =
        ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = ColorPrimaryDark),
    ) {
      Text(buttonText.uppercase(), fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun DateRow(item: TimelineItem.DateItem) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(color = ColorPrimary, shape = RoundedCornerShape(12.dp)) {
      Text(
        text = item.text,
        color = Color.White.copy(alpha = 0.4f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun TimeRow(item: TimelineItem.TimeItem) {
  Text(
    text = item.text,
    color = ColorFadedText,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun EntryRow(item: TimelineItem.EntryItem, onClick: () -> Unit, onEdit: () -> Unit) {
  val entry = item.entry
  Row(
    modifier =
      Modifier.fillMaxWidth()
        // Long-press opens the edit dialog directly; tap re-logs the activity
        .pointerInput(entry) {
          detectTapGestures(onLongPress = { onEdit() }, onTap = { onClick() })
        }
        .padding(start = 16.dp, end = 20.dp),
    verticalAlignment = Alignment.Top,
  ) {
    val entryStyle = MaterialTheme.typography.bodyLarge
    Text(text = entry.activity, color = ColorSecondaryText, style = entryStyle)
    Spacer(Modifier.width(8.dp))
    Text(
      text = entry.note.orEmpty(),
      color = ColorFadedText,
      style = entryStyle,
      modifier = Modifier.weight(1f),
    )
    Text(text = item.duration, color = ColorSecondaryText, style = entryStyle)
  }
}

@Composable
private fun AddEntryBar(onAdd: (String, String) -> Unit) {
  var activity by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val enabled = activity.isNotBlank()
  Row(
    modifier = Modifier.fillMaxWidth().background(ColorPrimary).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    InlineTextField(
      value = activity,
      onValueChange = { activity = it },
      hint = "Activity",
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(8.dp))
    InlineTextField(
      value = note,
      onValueChange = { note = it },
      hint = "Note",
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(8.dp))
    Button(
      onClick = {
        onAdd(activity, note)
        activity = ""
        note = ""
      },
      enabled = enabled,
      shape = ButtonShape,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = ColorAccent,
          contentColor = ColorPrimaryDark,
          disabledContainerColor = ColorAccent.copy(alpha = 0.5f),
          disabledContentColor = ColorPrimaryDark.copy(alpha = 0.5f),
        ),
    ) {
      Text("ADD", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun InlineTextField(
  value: String,
  onValueChange: (String) -> Unit,
  hint: String,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = LocalTextStyle.current.copy(color = Color.White),
    cursorBrush = SolidColor(Color.White),
    interactionSource = interactionSource,
    modifier = modifier,
    decorationBox = { inner ->
      Column {
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
          if (value.isEmpty()) {
            Text(hint, color = Color.White.copy(alpha = 0.5f), style = LocalTextStyle.current)
          }
          inner()
        }
        // Restores the old EditText underline: accent when focused, faded otherwise
        HorizontalDivider(color = if (focused) ColorAccent else Color.White.copy(alpha = 0.5f))
      }
    },
  )
}

/**
 * Auto-focuses this field (popping the soft keyboard) as a dialog opens, replacing the old fixed
 * `delay(100)` guess. Requesting focus needs two things to be true at once: the field's node must
 * be laid out, and the dialog window must hold focus (else the IME can't attach). We wait for both
 * via [onGloballyPositioned] and [WindowInfo.isWindowFocused], then request once. Gating on
 * placement is what fixes the intermittent miss: a bare window-focus listener can fire its only
 * emission before the field is placed, and since the value never changes again it's never retried
 */
@Composable
private fun Modifier.autoFocus(): Modifier {
  val focusRequester = remember { FocusRequester() }
  val windowInfo = LocalWindowInfo.current
  var placed by remember { mutableStateOf(false) }
  LaunchedEffect(focusRequester) {
    snapshotFlow { placed && windowInfo.isWindowFocused }.first { it }
    focusRequester.requestFocus()
  }
  return focusRequester(focusRequester).onGloballyPositioned { placed = true }
}

@Composable
private fun SearchDialog(
  initial: String,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit,
  onClear: () -> Unit,
) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Search timeline") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        // Auto-focus (and pop the keyboard) when the dialog opens, like the old AppCompat dialog
        modifier = Modifier.fillMaxWidth().autoFocus(),
      )
    },
    confirmButton = { TextButton(onClick = { onSubmit(text) }) { Text("Go") } },
    dismissButton = { TextButton(onClick = onClear) { Text("Clear") } },
    containerColor = ColorPrimaryDark,
  )
}

@Composable
private fun EntryEditDialog(
  entry: Entry,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
  onConfirm: (startTime: String, activity: String, note: String) -> Unit,
) {
  var startTime by remember { mutableStateOf("") }
  var activity by remember { mutableStateOf(entry.activity) }
  var note by remember { mutableStateOf(entry.note.orEmpty()) }
  // Title shows the entry's reverse-geocoded location, resolved asynchronously
  val title by
    produceState("Locating…", entry) {
      // Strip the trailing zip code and country from the displayed address only (cache keeps it)
      value = geocodeEntry(entry)?.replace(addressSuffixRegex, "") ?: "No location"
    }
  // Faded labels so the resting (placeholder-position) label reads as a hint, dimmer than input
  // text
  val fieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedLabelColor = ColorFadedText,
      unfocusedLabelColor = ColorFadedText,
    )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = MaterialTheme.typography.bodyMedium) },
    text = {
      // AlertDialog forces bodyMedium onto its text slot; restore bodyLarge for all fields at once
      ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
        Column {
          OutlinedTextField(
            value = activity,
            onValueChange = { activity = it },
            label = { Text("Activity") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
          OutlinedTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = { Text("Start time") },
            singleLine = true,
            colors = fieldColors,
            // Auto-focus the time field (and pop the keyboard) when the dialog opens
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).autoFocus(),
          )
        }
      }
    },
    confirmButton = {
      // Delete sits far left (away from Cancel/Save) so it's harder to misclick
      Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDelete) { Text("Delete") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(onClick = { onConfirm(startTime, activity, note) }) { Text("Save") }
      }
    },
    containerColor = ColorPrimaryDark,
  )
}

@Composable
fun NfcDialog(
  state: NfcDialogState,
  onActivityChange: (String) -> Unit,
  onNoteChange: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val title =
    when (state) {
      is NfcDialogState.Initial -> "Found new NFC tag"
      is NfcDialogState.Mismatch -> "Unusable NFC tag"
      is NfcDialogState.Ready -> "Register NFC tag"
    }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        when (state) {
          is NfcDialogState.Initial ->
            Text(
              "Tap the NFC tag again to confirm that you'd like to use it for logging new" +
                " activities."
            )
          is NfcDialogState.Mismatch ->
            Text(
              "Sorry, Chronofile can't register this NFC tag because its serial number changes" +
                " every time you tap it, a security measure implemented by some devices such as" +
                " phones and credit cards."
            )
          is NfcDialogState.Ready -> {
            Text(
              "Enter the activity name and note to record whenever you tap this tag from now on." +
                " You can edit or delete this later in Settings."
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
              OutlinedTextField(
                value = state.activity,
                onValueChange = onActivityChange,
                label = { Text("Activity") },
                singleLine = true,
                // Auto-focus the activity field (and pop the keyboard) once we reach the Ready step
                modifier = Modifier.weight(1f).padding(end = 4.dp).autoFocus(),
              )
              OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      when (state) {
        is NfcDialogState.Ready ->
          TextButton(onClick = onConfirm, enabled = state.activity.isNotBlank()) { Text("OK") }
        is NfcDialogState.Mismatch -> TextButton(onClick = onDismiss) { Text("OK") }
        is NfcDialogState.Initial -> Unit
      }
    },
    dismissButton = {
      if (state !is NfcDialogState.Mismatch) TextButton(onClick = onDismiss) { Text("Cancel") }
    },
    containerColor = ColorPrimaryDark,
  )
}
