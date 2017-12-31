package com.chaidarun.chronofile

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.form_entry.view.*
import kotlinx.android.synthetic.main.item_date.view.*
import kotlinx.android.synthetic.main.item_entry.view.*
import org.jetbrains.anko.toast
import java.text.SimpleDateFormat
import java.util.*

private enum class ViewType(val id: Int) { DATE(0), ENTRY(1) }
sealed class ListItem(val typeCode: Int)
private class DateItem(val date: Date) : ListItem(ViewType.DATE.id)
private class EntryItem(val entry: Entry) : ListItem(ViewType.ENTRY.id)

class HistoryListAdapter(
  private val appActivity: AppCompatActivity
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  private var itemList = listOf<ListItem>()
  private val selectedEntries = mutableListOf<Entry>()
  private val receiver by lazy {
    object : ResultReceiver(Handler()) {
      override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
        if (resultCode == FetchAddressIntentService.SUCCESS_CODE) {
          App.ctx.toast(resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY))
        }
      }
    }
  }
  private val actionModeCallback by lazy {
    object : ActionMode.Callback {
      override fun onActionItemClicked(mode: ActionMode, item: MenuItem?): Boolean {
        when (item?.itemId) {
          R.id.delete -> Store.dispatch(Action.RemoveEntries(selectedEntries.map { it.startTime }))
          R.id.edit -> {
            val entry = selectedEntries[0]
            val view = LayoutInflater.from(appActivity).inflate(R.layout.form_entry, null)
            with(AlertDialog.Builder(appActivity)) {
              setTitle("Edit entry")
              view.formEntryStartTime.setText(entry.startTime.toString())
              view.formEntryActivity.setText(entry.activity)
              view.formEntryNote.setText(entry.note ?: "")
              setView(view)
              setPositiveButton("OK", { _, _ ->
                Store.dispatch(Action.EditEntry(
                  entry.startTime, view.formEntryStartTime.text.toString(),
                  view.formEntryActivity.text.toString(), view.formEntryNote.text.toString()))
                appActivity.toast("Updated entry")
              })
              setNegativeButton("Cancel", { dialog, _ -> dialog.cancel() })
              show()
            }
          }
          R.id.location -> {
            val entry = selectedEntries.getOrNull(0)
            if (entry?.latLong == null) {
              App.ctx.toast("No location data available")
            } else {
              val location = Location("dummyprovider").apply {
                latitude = entry.latLong[0]
                longitude = entry.latLong[1]
              }
              val intent = Intent(App.ctx, FetchAddressIntentService::class.java)
              intent.putExtra(FetchAddressIntentService.RECEIVER, receiver)
              intent.putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, location)
              App.ctx.startService(intent)
            }
          }
          else -> App.ctx.toast("Unknown action!")
        }
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

  init {
    var cache: History? = null
    Store.state.subscribe {
      if (cache == it.history) {
        return@subscribe
      }
      cache = it.history

      Log.d(TAG, "Rendering history view")
      itemList = (mutableListOf<ListItem>()).apply {
        var currentDate = Date(0)
        it.history?.entries?.forEach {
          val entryDate = Date(it.startTime * 1000)
          if (DATE_FORMAT.format(entryDate) != DATE_FORMAT.format(currentDate)) {
            currentDate = entryDate
            add(DateItem(entryDate))
          }
          add(EntryItem(it))
        }
      }.toList()
      notifyDataSetChanged()
      appActivity.historyList.scrollToPosition(itemList.size - 1)
    }
  }

  override fun getItemCount() = itemList.size
  override fun getItemViewType(position: Int) = itemList[position].typeCode

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
    ViewType.DATE.id -> DateViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false))
    ViewType.ENTRY.id -> EntryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false))
    else -> null
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindItem(itemList[position])
  }

  abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bindItem(listItem: ListItem): Any
  }

  class DateViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      with((listItem as DateItem).date) { itemView.date.text = DATE_FORMAT.format(this) }
    }
  }

  inner class EntryViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      with((listItem as EntryItem).entry) {
        itemView.entryActivity.text = activity
        itemView.entryNote.text = note
        itemView.entryNote.visibility = if (note == null) View.GONE else View.VISIBLE
        itemView.entryStartTime.text = TIME_FORMAT.format(Date(startTime * 1000))
        itemView.setOnClickListener { History.addEntry(activity, note) }
        itemView.setOnLongClickListener {
          (itemView.context as AppCompatActivity).startActionMode(actionModeCallback)
          selectedEntries.clear()
          selectedEntries.add(this)
          true
        }
      }
    }
  }

  companion object {
    private val TAG = "HistoryListAdapter"
    private val DATE_FORMAT = SimpleDateFormat("EE, dd MMM YYYY", Locale.getDefault())
    private val TIME_FORMAT = SimpleDateFormat("H:mm", Locale.getDefault())
  }
}
