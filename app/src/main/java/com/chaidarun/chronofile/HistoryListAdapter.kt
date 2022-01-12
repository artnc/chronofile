package com.chaidarun.chronofile

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.Date
import kotlin.system.measureTimeMillis
import kotlinx.android.synthetic.main.content_main.historyList
import kotlinx.android.synthetic.main.form_entry.view.formEnableStartTime
import kotlinx.android.synthetic.main.form_entry.view.formEntryActivity
import kotlinx.android.synthetic.main.form_entry.view.formEntryNote
import kotlinx.android.synthetic.main.form_entry.view.formEntryStartTime
import kotlinx.android.synthetic.main.item_date.view.date
import kotlinx.android.synthetic.main.item_entry.view.entryActivity
import kotlinx.android.synthetic.main.item_entry.view.entryDuration
import kotlinx.android.synthetic.main.item_entry.view.entryNote
import kotlinx.android.synthetic.main.item_time.view.time

private enum class ViewType {
  DATE,
  ENTRY,
  SPACER,
  TIME
}

sealed class ListItem(val viewType: ViewType)

private data class DateItem(val date: Date) : ListItem(ViewType.DATE)

private data class EntryItem(val entry: Entry, val itemStart: Long, val itemEnd: Long) :
  ListItem(ViewType.ENTRY)

private data class SpacerItem(val height: Int) : ListItem(ViewType.SPACER)

private data class TimeItem(val time: Date) : ListItem(ViewType.TIME)

class HistoryListAdapter(private val appActivity: AppCompatActivity) :
  RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  private var itemList = listOf<ListItem>()
  private var itemListLength = 0
  private var selectedEntry: Entry? = null
  private val receiver by lazy {
    object : ResultReceiver(Handler()) {
      override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
        if (resultCode == FetchAddressIntentService.SUCCESS_CODE) {
          resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY)?.let { App.toast(it) }
        }
      }
    }
  }
  private val actionModeCallback by lazy {
    object : ActionMode.Callback {
      override fun onActionItemClicked(mode: ActionMode, item: MenuItem?): Boolean {
        val entry = selectedEntry
        if (entry != null) {
          when (item?.itemId) {
            R.id.delete -> Store.dispatch(Action.RemoveEntry(entry.startTime))
            R.id.edit -> {
              val view = LayoutInflater.from(appActivity).inflate(R.layout.form_entry, null)
              view.formEnableStartTime.text = formatTime(entry.startTime)
              view.formEnableStartTime.setOnClickListener {
                it.visibility = View.GONE
                view.formEntryStartTime.visibility = View.VISIBLE
              }
              view.formEntryStartTime.setIs24HourView(true);
              val timeDetails = getTimeDetails(entry.startTime)
              view.formEntryStartTime.hour = timeDetails.first
              view.formEntryStartTime.minute = timeDetails.second
              with(AlertDialog.Builder(appActivity, R.style.MyAlertDialogTheme)) {
                setTitle("Edit entry")
                view.formEntryActivity.setText(entry.activity)
                view.formEntryNote.setText(entry.note ?: "")
                setView(view)
                setPositiveButton("OK") { _, _ ->
                  Store.dispatch(
                    Action.EditEntry(
                      entry.startTime,
                      formatTime(buildTimeDetails(view.formEntryStartTime.hour, view.formEntryStartTime.minute)),
                      view.formEntryActivity.text.toString(),
                      view.formEntryNote.text.toString()
                    )
                  )
                }
                setNegativeButton("Cancel", null)
                show()
              }
            }
            R.id.location -> {
              if (entry.latLong == null) {
                App.toast("No location data available")
              } else {
                val location =
                  Location("dummyprovider").apply {
                    latitude = entry.latLong.first
                    longitude = entry.latLong.second
                  }
                val intent = Intent(App.ctx, FetchAddressIntentService::class.java)
                intent.putExtra(FetchAddressIntentService.RECEIVER, receiver)
                intent.putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, location)
                App.ctx.startService(intent)
              }
            }
            else -> App.toast("Unknown action!")
          }
        }
        selectedEntry = null
        mode.finish()
        return true
      }

      override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.menu_edit, menu)
        return true
      }

      override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?) = false
      override fun onDestroyActionMode(mode: ActionMode?) {
        selectedEntry = null
        // for updating the checked state
        notifyDataSetChanged();
      }
    }
  }

  private val subscription =
    Store.observable.map { it.history }.distinctUntilChanged().subscribe { history ->
      if (history == null) {
        Log.i(TAG, "History is null")
        return@subscribe
      }

      val elapsedMs = measureTimeMillis {
        val items = mutableListOf<ListItem>()
        items.add(SpacerItem(32))
        var lastSeenStartTime = history.currentActivityStartTime
        history.entries.takeLast(MAX_ENTRIES_SHOWN).reversed().forEach { entry ->
          items.add(TimeItem(Date(lastSeenStartTime * 1000)))

          // Use either one or two items for entry depending on whether it crosses midnight
          if (formatDate(entry.startTime) != formatDate(lastSeenStartTime)) {
            val midnight = getPreviousMidnight(lastSeenStartTime)
            items.add(EntryItem(entry, midnight, lastSeenStartTime))
            items.add(DateItem(Date(lastSeenStartTime * 1000)))
            items.add(EntryItem(entry, entry.startTime, midnight))
          } else {
            items.add(EntryItem(entry, entry.startTime, lastSeenStartTime))
          }

          lastSeenStartTime = entry.startTime
        }
        items.add(SpacerItem(32))
        itemList = items.reversed()
        itemListLength = itemList.size
        notifyDataSetChanged()
        appActivity.historyList.scrollToPosition(itemList.size - 1)
      }
      Log.i(TAG, "Rendered history view in $elapsedMs ms")
    }

  override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
    subscription.dispose()
    super.onDetachedFromRecyclerView(recyclerView)
  }

  override fun getItemCount() = itemListLength
  override fun getItemViewType(position: Int) = itemList[position].viewType.ordinal

  override fun onCreateViewHolder(parent: ViewGroup, viewTypeOrdinal: Int) =
    when (ViewType.values()[viewTypeOrdinal]) {
      ViewType.DATE ->
        DateViewHolder(
          LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false)
        )
      ViewType.ENTRY ->
        EntryViewHolder(
          LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false) as EntryView
        )
      ViewType.TIME ->
        TimeViewHolder(
          LayoutInflater.from(parent.context).inflate(R.layout.item_time, parent, false)
        )
      ViewType.SPACER ->
        SpacerViewHolder(
          LinearLayout(appActivity).apply {
            layoutParams =
              LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
              )
          }
        )
    }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindItem(itemList[position])
  }

  companion object {
    /** We limit shown entries because showing all can be slow */
    private const val MAX_ENTRIES_SHOWN = 1000
  }

  abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bindItem(listItem: ListItem): Any
  }

  class DateViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      itemView.date.text = (listItem as? DateItem)?.date?.let { formatDate(it) } ?: ""
    }
  }

  inner class EntryViewHolder(view: EntryView) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      val (entry, itemStart, itemEnd) = listItem as EntryItem
      val activity = entry.activity
      val note = entry.note

      with(itemView as EntryView) {
        entryActivity.text = activity
        entryNote.text = note
        entryDuration.text = formatDuration(itemEnd - itemStart)
        setOnClickListener { History.addEntry(activity, note) }
        setOnLongClickListener {
          (context as AppCompatActivity).startActionMode(actionModeCallback)
          selectedEntry = entry
          isChecked = true;
          notifyDataSetChanged();
          true
        }
        itemView.isChecked = (selectedEntry == entry);
      }
    }
  }

  class SpacerViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      with(itemView as LinearLayout) {
        layoutParams.height = (listItem as SpacerItem).height
        requestLayout()
      }
    }
  }

  class TimeViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      itemView.time.text = formatTime((listItem as TimeItem).time)
    }
  }
}
