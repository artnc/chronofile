package com.chaidarun.chronofile

import android.util.Log
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay

/** All actions must be immutable */
sealed class Action {
  data class AddEntry(
    val activity: String,
    val note: String?,
    val latLong: List<Double>?
  ) : Action()

  data class EditEntry(
    val oldStartTime: Long,
    val newStartTime: String,
    val activity: String,
    val note: String
  ) : Action()

  data class RemoveEntries(val entries: List<Long>) : Action()
  data class SetConfig(val config: Config) : Action()
  data class SetGraphGrouping(val grouped: Boolean) : Action()
  data class SetGraphMetric(val metric: Metric) : Action()
  data class SetHistory(val history: History) : Action()
}

/** This class must be deeply immutable and preferably printable */
data class State(
  val config: Config? = null,
  val history: History? = null,
  val graphSettings: GraphSettings = GraphSettings()
)

private val reducer: (State, Action) -> State = { state, action ->
  Log.d("Reducer", "Reducing $action")
  val nextState = when (action) {
    is Action.AddEntry -> state.copy(history = state.history?.withNewEntry(action.activity, action.note, action.latLong))
    is Action.EditEntry -> state.copy(history = state.history?.withEditedEntry(action.oldStartTime, action.newStartTime, action.activity, action.note))
    is Action.RemoveEntries -> state.copy(history = state.history?.withoutEntries(action.entries))
    is Action.SetConfig -> state.copy(config = action.config)
    is Action.SetGraphGrouping -> state.copy(graphSettings = state.graphSettings.copy(grouped = action.grouped))
    is Action.SetGraphMetric -> state.copy(graphSettings = state.graphSettings.copy(metric = action.metric))
    is Action.SetHistory -> state.copy(history = action.history)
  }
  Log.d("Reducer", "State is now $nextState")
  nextState
}

/** API heavily inspired by Redux */
object Store {

  private val TAG = "Store"
  private val actions = PublishRelay.create<Action>()
  val state: BehaviorRelay<State> = BehaviorRelay.create()

  init {
    actions.scan(State(), reducer).subscribe { state.accept(it) }
  }

  fun dispatch(action: Action) {
    Log.d(TAG, "Dispatching $action")
    actions.accept(action)
  }
}
