// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All actions must be immutable */
sealed class Action {
  data class AddEntry(val activity: String, val note: String?, val latLon: Pair<Double, Double>?) :
    Action()

  data class EditEntry(
    val oldStartTime: Long,
    val newStartTime: String,
    val activity: String,
    val note: String,
  ) : Action()

  data class RegisterNfcTag(val id: String, val entry: List<String>) : Action()

  data class RemoveEntry(val startTime: Long) : Action()

  data class SetConfigFromText(val text: String) : Action()

  data class SetConfigFromFile(val config: Config) : Action()

  data class SetGraphCountMetric(val countMetric: CountMetric) : Action()

  data class SetGraphGrouping(val grouped: Boolean) : Action()

  data class SetGraphMetric(val metric: Metric) : Action()

  data class SetGraphRangeEnd(val timestamp: Long) : Action()

  data class SetGraphRangeStart(val timestamp: Long) : Action()

  data class SetGraphStacking(val stacked: Boolean) : Action()

  data class SetHistory(val history: History) : Action()

  data class SetSearchQuery(val query: String?) : Action()
}

/** This class must be deeply immutable and preferably printable */
data class State(
  val config: Config? = null,
  val history: History? = null,
  val graphConfig: GraphConfig = GraphConfig(),
  val searchQuery: String? = null,
)

private fun reduce(state: State, action: Action): State =
  with(state) {
    when (action) {
      is Action.AddEntry ->
        copy(history = history?.withNewEntry(action.activity, action.note, action.latLon))
      is Action.EditEntry ->
        copy(
          history =
            history?.withEditedEntry(
              action.oldStartTime,
              action.newStartTime,
              action.activity,
              action.note,
            )
        )
      is Action.RegisterNfcTag -> {
        val oldConfig = config ?: Config()
        val oldNfcTags = oldConfig.nfcTags ?: mutableMapOf()
        val newConfig = oldConfig.copy(nfcTags = oldNfcTags + (action.id to action.entry))
        newConfig.save()
        copy(config = newConfig)
      }
      is Action.RemoveEntry -> copy(history = history?.withoutEntry(action.startTime))
      is Action.SetConfigFromText ->
        try {
          val config = Config.fromText(action.text)
          App.toast("Saved config")
          copy(config = config)
        } catch (_: Throwable) {
          App.toast("Failed to save invalid config")
          this
        }
      is Action.SetConfigFromFile -> copy(config = action.config)
      is Action.SetGraphCountMetric ->
        copy(graphConfig = graphConfig.copy(countMetric = action.countMetric))
      is Action.SetGraphGrouping -> copy(graphConfig = graphConfig.copy(grouped = action.grouped))
      is Action.SetGraphMetric -> copy(graphConfig = graphConfig.copy(metric = action.metric))
      is Action.SetGraphRangeEnd -> {
        val ts = action.timestamp
        val clamped = ts < (graphConfig.startTime ?: 0)
        copy(
          graphConfig =
            graphConfig.copy(endTime = ts, startTime = if (clamped) ts else graphConfig.startTime)
        )
      }
      is Action.SetGraphRangeStart -> {
        val ts = action.timestamp
        val clamped = ts > (graphConfig.endTime ?: Long.MAX_VALUE)
        copy(
          graphConfig =
            graphConfig.copy(startTime = ts, endTime = if (clamped) ts else graphConfig.endTime)
        )
      }
      is Action.SetGraphStacking -> copy(graphConfig = graphConfig.copy(stacked = action.stacked))
      is Action.SetHistory -> copy(history = action.history)
      is Action.SetSearchQuery -> copy(searchQuery = action.query)
    }
  }

/** Holds the deeply-immutable app [State] and reduces [Action]s into it, Redux-style */
class MainViewModel : ViewModel() {

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  @Synchronized
  fun dispatch(action: Action) {
    val start = System.currentTimeMillis()
    _state.update { reduce(it, action) }
    Log.i(TAG, "Reduced $action in ${System.currentTimeMillis() - start} ms")
  }

  /**
   * Loads config and history off the main thread, then publishes them into state. No-ops if state
   * is already loaded (e.g. Activity recreated on rotation) unless [force]; [force] is used when
   * the save directory changes and we must re-read from the new location.
   */
  fun hydrate(force: Boolean = false) {
    if (!force && state.value.history != null) return
    viewModelScope.launch {
      Log.i(TAG, "Hydrating store from files")
      val (config, history) =
        withContext(Dispatchers.IO) { Config.fromFile() to History.fromFile() }
      dispatch(Action.SetConfigFromFile(config))
      dispatch(Action.SetHistory(history))
    }
  }

  /** Acquires the current location (if granted) before recording a new entry */
  fun addEntry(activity: String, note: String?) {
    viewModelScope.launch {
      dispatch(Action.AddEntry(activity, note, History.getCurrentLocation()))
      App.toast("Recorded $activity")
    }
  }
}
