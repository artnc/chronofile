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

  data class RemoveEntries(val entries: Collection<Long>) : Action()
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
  Log.d(TAG, "Reducing $action")
  with(state) {
    val nextState = when (action) {
      is Action.AddEntry -> copy(history = history?.withNewEntry(
        action.activity, action.note, action.latLong))
      is Action.EditEntry -> copy(history = history?.withEditedEntry(
        action.oldStartTime, action.newStartTime, action.activity, action.note))
      is Action.RemoveEntries -> copy(history = history?.withoutEntries(action.entries))
      is Action.SetConfig -> copy(config = action.config)
      is Action.SetGraphGrouping -> copy(
        graphSettings = graphSettings.copy(grouped = action.grouped))
      is Action.SetGraphMetric -> copy(graphSettings = graphSettings.copy(metric = action.metric))
      is Action.SetHistory -> copy(history = action.history)
    }
    Log.d(TAG, "State diff: ${dumbDiff(this, nextState)}")
    nextState
  }
}

/** API heavily inspired by Redux */
object Store {

  /**
   * This exposes `.value` (analogous to Redux `store.getState`) and `.subscribe` (analogous to
   * Redux `store.subscribe`).
   *
   * TODO: Wrap this object to hide its `.accept` from public callers?
   * */
  val state: BehaviorRelay<State> = BehaviorRelay.create()
  private val actions = PublishRelay.create<Action>().apply {
    scan(State(), reducer).subscribe { state.accept(it) }
  }

  fun dispatch(action: Action) = actions.accept(action)
}
