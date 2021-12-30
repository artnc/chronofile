package com.chaidarun.chronofile

import android.util.Log
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable

/** All actions must be immutable */
sealed class Action {
  data class AddEntry(val activity: String, val note: String?, val latLong: Pair<Double, Double>?) :
    Action()

  data class EditEntry(
    val oldStartTime: Long,
    val newStartTime: String,
    val activity: String,
    val note: String
  ) : Action()

  data class RemoveEntry(val entry: Long) : Action()
  data class SetConfigFromText(val text: String) : Action()
  data class SetConfigFromFile(val config: Config) : Action()
  data class SetGraphGrouping(val grouped: Boolean) : Action()
  data class SetGraphMetric(val metric: Metric) : Action()
  data class SetGraphRangeEnd(val timestamp: Long) : Action()
  data class SetGraphRangeStart(val timestamp: Long) : Action()
  data class SetGraphStacking(val stacked: Boolean) : Action()
  data class SetHistory(val history: History) : Action()
}

/** This class must be deeply immutable and preferably printable */
data class State(
  val config: Config? = null,
  val history: History? = null,
  val graphConfig: GraphConfig = GraphConfig()
)

private val reducer: (State, Action) -> State = { state, action ->
  with(state) {
    val start = System.currentTimeMillis()
    val nextState =
      when (action) {
        is Action.AddEntry ->
          copy(history = history?.withNewEntry(action.activity, action.note, action.latLong))
        is Action.EditEntry ->
          copy(
            history =
              history?.withEditedEntry(
                action.oldStartTime,
                action.newStartTime,
                action.activity,
                action.note
              )
          )
        is Action.RemoveEntry -> copy(history = history?.withoutEntry(action.entry))
        is Action.SetConfigFromText ->
          try {
            val config = Config.fromText(action.text)
            App.toast("Saved config")
            copy(config = config)
          } catch (e: Throwable) {
            App.toast("Failed to save invalid config")
            this
          }
        is Action.SetConfigFromFile -> copy(config = action.config)
        is Action.SetGraphGrouping -> copy(graphConfig = graphConfig.copy(grouped = action.grouped))
        is Action.SetGraphMetric -> copy(graphConfig = graphConfig.copy(metric = action.metric))
        is Action.SetGraphRangeEnd -> {
          val timestamp = action.timestamp
          val newSettings =
            if (timestamp >= state.graphConfig.startTime ?: 0) {
              graphConfig.copy(endTime = timestamp)
            } else {
              graphConfig.copy(endTime = timestamp, startTime = timestamp)
            }
          copy(graphConfig = newSettings)
        }
        is Action.SetGraphRangeStart -> {
          val timestamp = action.timestamp
          val newSettings =
            if (timestamp <= state.graphConfig.endTime ?: Long.MAX_VALUE) {
              graphConfig.copy(startTime = timestamp)
            } else {
              graphConfig.copy(endTime = timestamp, startTime = timestamp)
            }
          copy(graphConfig = newSettings)
        }
        is Action.SetGraphStacking -> copy(graphConfig = graphConfig.copy(stacked = action.stacked))
        is Action.SetHistory -> copy(history = action.history)
      }

    Log.i(TAG, "Reduced $action in ${System.currentTimeMillis() - start} ms")
    nextState
  }
}

/** API heavily inspired by Redux */
object Store {

  private val stateRelay: BehaviorRelay<State> = BehaviorRelay.create()
  private val actionRelay =
    PublishRelay.create<Action>().apply {
      scan(State(), reducer).distinctUntilChanged().subscribe { stateRelay.accept(it) }
    }

  val state: State
    get() = stateRelay.value

  val observable: Observable<State>
    get() = stateRelay

  fun dispatch(action: Action) = actionRelay.accept(action)
}
