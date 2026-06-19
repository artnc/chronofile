// © Art Chaidarun

package com.chaidarun.chronofile

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations; see [androidx.navigation.compose.composable] reified overload
 */
@Serializable data object Earth

@Serializable data object Editor

@Serializable data object Graph

@Serializable data object Timeline

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()
  private var refreshTick by mutableIntStateOf(0)
  private var nfcState by mutableStateOf<NfcDialogState?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    Log.d(TAG, "MainActivity onCreate")
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    viewModel.hydrate()

    setContent {
      ChronofileTheme {
        val navController = rememberNavController()
        NavHost(
          navController = navController,
          startDestination = Timeline,
          // Disable default fade crossfade between destinations
          enterTransition = { EnterTransition.None },
          exitTransition = { ExitTransition.None },
          popEnterTransition = { EnterTransition.None },
          popExitTransition = { ExitTransition.None },
        ) {
          composable<Timeline> {
            TimelineScreen(
              viewModel = viewModel,
              refreshTick = refreshTick,
              onOpenEarth = { navController.navigate(Earth) },
              onOpenSettings = { navController.navigate(Editor) },
              onOpenStats = { navController.navigate(Graph) },
            )
          }
          composable<Earth> {
            EarthScreen(viewModel = viewModel, onNavigateUp = { navController.popBackStack() })
          }
          composable<Editor> {
            EditorScreen(
              initialText = viewModel.state.value.config?.serialize() ?: "",
              onSave = { viewModel.dispatch(Action.SetConfigFromText(it)) },
              onNavigateUp = { navController.popBackStack() },
            )
          }
          composable<Graph> {
            GraphScreen(viewModel = viewModel, onNavigateUp = { navController.popBackStack() })
          }
        }
        nfcState?.let { nfc ->
          NfcDialog(
            state = nfc,
            onActivityChange = { v ->
              (nfc as? NfcDialogState.Ready)?.let { nfcState = it.copy(activity = v) }
            },
            onNoteChange = { v ->
              (nfc as? NfcDialogState.Ready)?.let { nfcState = it.copy(note = v) }
            },
            onConfirm = {
              if (nfc is NfcDialogState.Ready && nfc.activity.isNotBlank()) {
                val inputs = listOfNotNull(nfc.activity, nfc.note.takeIf { it.isNotBlank() })
                viewModel.dispatch(Action.RegisterNfcTag(nfc.tagId, inputs))
                App.toast("NFC tag registered - try tapping it!")
              }
              nfcState = null
            },
            onDismiss = { nfcState = null },
          )
        }
      }
    }
  }

  override fun onResume() {
    Log.d(TAG, "MainActivity onResume")
    super.onResume()
    // Reformat times in case time zone changed
    refreshTick++
    if (intent.action in NFC_INTENT_ACTIONS) processNfcIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    Log.d(TAG, "MainActivity onNewIntent")
    super.onNewIntent(intent)
    if (intent.action in NFC_INTENT_ACTIONS) processNfcIntent(intent)
  }

  private fun processNfcIntent(intent: Intent) {
    // Prevent intent from being processed by multiple lifecycle events
    setIntent(Intent())

    val tag =
      IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
    val id = tag.id.toHexString(HexFormat.UpperCase)
    Log.i(TAG, "Detected NFC tag: $id")
    val entryToAdd = viewModel.state.value.config?.nfcTags?.get(id)
    val current = nfcState
    nfcState =
      when {
        entryToAdd != null -> {
          viewModel.addEntry(entryToAdd[0], entryToAdd.getOrNull(1))
          return
        }
        current == null -> NfcDialogState.Initial(tagId = id)
        current.tagId != id -> NfcDialogState.Mismatch(tagId = current.tagId)
        else -> NfcDialogState.Ready(tagId = current.tagId)
      }
  }

  companion object {
    private val NFC_INTENT_ACTIONS =
      arrayOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED,
      )
  }
}

sealed interface NfcDialogState {
  val tagId: String

  data class Initial(override val tagId: String) : NfcDialogState

  data class Mismatch(override val tagId: String) : NfcDialogState

  data class Ready(override val tagId: String, val activity: String = "", val note: String = "") :
    NfcDialogState
}
