package com.chaidarun.chronofile

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.*
import kotlinx.android.synthetic.main.entry_history.view.*
import org.jetbrains.anko.toast
import java.text.SimpleDateFormat
import java.util.*


class HistoryListAdapter(private val history: History, private val itemClick: (Entry) -> Unit) :
  RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

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

      override fun onDestroyActionMode(mode: ActionMode?) {
        notifyDataSetChanged()
      }
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.entry_history, parent, false), itemClick, this)

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindEntry(history.entries[position])
  }

  override fun getItemCount() = history.entries.size

  inner class ViewHolder(
    private val view: View,
    private val itemClick: (Entry) -> Unit,
    private val adapter: HistoryListAdapter
  ) : RecyclerView.ViewHolder(view) {

    fun bindEntry(entry: Entry) {
      with(entry) {
        itemView.entryActivity.text = activity
        itemView.entryNote.text = note
        itemView.entryNote.visibility = if (note == null) View.GONE else View.VISIBLE
        itemView.entryStartTime.text = SimpleDateFormat("MMM dd HH:mm").format(Date(startTime * 1000))
        itemView.setOnClickListener {
          itemClick(this)
          adapter.notifyDataSetChanged()
        }
        itemView.setOnLongClickListener {
          (view.context as AppCompatActivity).startActionMode(mActionModeCallback)
          selectedEntries.clear()
          selectedEntries.add(this)
          true
        }
      }
    }
  }
}
