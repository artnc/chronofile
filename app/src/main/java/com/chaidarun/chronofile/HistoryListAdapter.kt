package com.chaidarun.chronofile

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.*
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
  private val recyclerView: RecyclerView,
  private val history: History,
  private val itemClick: (Entry) -> Unit
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  companion object {
    private val DATE_FORMAT = SimpleDateFormat("EEEE, dd MMMM YYYY", Locale.getDefault())
    private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
  }

  private val itemList = mutableListOf<ListItem>()
  private val selectedEntries = mutableListOf<Entry>()
  private val mResultReceiver by lazy {
    object : ResultReceiver(Handler()) {
      override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
        if (resultCode == FetchAddressIntentService.SUCCESS_CODE) {
          App.ctx.toast(resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY))
        }
      }
    }
  }

  init {
    refreshItemList()
  }

  private fun refreshItemList() {
    itemList.clear()
    var currentDate = Date(0)
    history.entries.forEach {
      val entryDate = Date(it.startTime * 1000)
      if (DATE_FORMAT.format(entryDate) != DATE_FORMAT.format(currentDate)) {
        currentDate = entryDate
        itemList.add(DateItem(entryDate))
      }
      itemList.add(EntryItem(it))
    }
  }

  private val mActionModeCallback by lazy {
    object : ActionMode.Callback {
      override fun onActionItemClicked(mode: ActionMode, item: MenuItem?): Boolean {
        when (item?.itemId) {
          R.id.delete -> history.removeEntries(selectedEntries.map { it.startTime })
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
              intent.putExtra(FetchAddressIntentService.RECEIVER, mResultReceiver)
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
        mode?.menuInflater?.inflate(R.menu.menu_entry, menu)
        return true
      }

      override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?) = false
      override fun onDestroyActionMode(mode: ActionMode?) = refreshAdapter()
    }
  }

  override fun getItemCount() = itemList.size
  override fun getItemViewType(position: Int) = itemList[position].typeCode

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ) = when (viewType) {
    ViewType.DATE.id -> DateViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false))
    ViewType.ENTRY.id -> EntryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false), itemClick, this)
    else -> null
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindItem(itemList[position])
  }

  fun refreshAdapter() {
    refreshItemList()
    notifyDataSetChanged()
    recyclerView.scrollToPosition(itemList.size - 1)
  }

  abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bindItem(listItem: ListItem): Any
  }

  inner class EntryViewHolder(
    view: View,
    private val itemClick: (Entry) -> Unit,
    private val adapter: HistoryListAdapter
  ) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      with((listItem as EntryItem).entry) {
        itemView.entryActivity.text = activity
        itemView.entryNote.text = note
        itemView.entryNote.visibility = if (note == null) View.GONE else View.VISIBLE
        itemView.entryStartTime.text = TIME_FORMAT.format(Date(startTime * 1000))
        itemView.setOnClickListener {
          itemClick(this)
          adapter.refreshAdapter()
        }
        itemView.setOnLongClickListener {
          (itemView.context as AppCompatActivity).startActionMode(mActionModeCallback)
          selectedEntries.clear()
          selectedEntries.add(this)
          true
        }
      }
    }
  }

  class DateViewHolder(view: View) : ViewHolder(view) {
    override fun bindItem(listItem: ListItem) {
      with((listItem as DateItem).date) { itemView.date.text = DATE_FORMAT.format(this) }
    }
  }
}
