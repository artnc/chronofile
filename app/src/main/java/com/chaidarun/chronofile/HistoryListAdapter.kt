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
import java.util.*
import kotlin.system.measureTimeMillis
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.form_entry.view.*
import kotlinx.android.synthetic.main.item_date.view.*
import kotlinx.android.synthetic.main.item_entry.view.*
import kotlinx.android.synthetic.main.item_time.view.*

private enum class ViewType(val id: Int) { DATE(0), ENTRY(1), SPACER(2), TIME(3) }
sealed class ListItem(val typeCode: Int)
private data class DateItem(val date: Date) : ListItem(ViewType.DATE.id)
private data class EntryItem(
  val entry: Entry,
  val itemStart: Long,
  val itemEnd: Long
) : ListItem(ViewType.ENTRY.id)

private data class SpacerItem(val height: Int) : ListItem(ViewType.SPACER.id)
private data class TimeItem(val time: Date) : ListItem(ViewType.TIME.id)

class HistoryListAdapter(
  private val appActivity: AppCompatActivity
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

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
              with(AlertDialog.Builder(appActivity, R.style.MyAlertDialogTheme)) {
                setTitle("Edit entry")
                view.formEntryActivity.setText(entry.activity)
                view.formEntryNote.setText(entry.note ?: "")
                setView(view)
                setPositiveButton(
                  "OK"
                ) { _, _ ->
                  Store.dispatch(
                    Action.EditEntry(
                      entry.startTime, view.formEntryStartTime.text.toString(),
                      view.formEntryActivity.text.toString(), view.formEntryNote.text.toString()
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
                val location = Location("dummyprovider").apply {
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
      override fun onDestroyActionMode(mode: ActionMode?) {}
    }
  }

  private val subscription = Store.observable.map { it.history }.distinctUntilChanged().subscribe { history ->
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
        val entryCrossesMidnight = formatDate(entry.startTime) != formatDate(lastSeenStartTime)
        if (entryCrossesMidnight) {
          val midnight = with(Calendar.getInstance()) {
            apply { timeInMillis = lastSeenStartTime * 1000 }
            GregorianCalendar(
              get(Calendar.YEAR),
              get(Calendar.MONTH),
              get(Calendar.DAY_OF_MONTH)
            ).time.time / 1000
          }
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
  override fun getItemViewType(position: Int) = itemList[position].typeCode

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
    ViewType.DATE.id -> DateViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false)
    )
    ViewType.ENTRY.id -> EntryViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
    )
    ViewType.TIME.id -> TimeViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.item_time, parent, false)
    )
    ViewType.SPACER.id -> SpacerViewHolder(
      LinearLayout(appActivity).apply {
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
      }
    )
    else -> error("Invalid view type")
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

  inner class EntryViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      val (entry, itemStart, itemEnd) = listItem as EntryItem
      val activity = entry.activity
      val note = entry.note

      with(itemView) {
        entryActivity.text = activity
        entryNote.text = note
        entryNote.visibility = if (note == null) View.GONE else View.VISIBLE
        entryDuration.text = formatDuration(itemEnd - itemStart)
        setOnClickListener { History.addEntry(activity, note) }
        setOnLongClickListener {
          (context as AppCompatActivity).startActionMode(actionModeCallback)
          selectedEntry = entry
          true
        }
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
