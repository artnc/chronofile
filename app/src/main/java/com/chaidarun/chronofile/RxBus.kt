package com.chaidarun.chronofile

import android.util.Log
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

sealed class Action(val type: String)
class AddEntryAction(
  val activity: String,
  val note: String?,
  val callback: (Entry) -> Any
) : Action("ADD_ENTRY")
class AddedEntryAction : Action("ADDED_ENTRY")

object RxBus {

  private val TAG = "RxBus"
  private val publisher = PublishSubject.create<Action>()

  fun dispatch(action: Action) {
    Log.d(TAG, action.type)
    publisher.onNext(action)
  }

  fun listen(): Observable<Action> = publisher
}
