package com.chaidarun.chronofile

import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.*
import kotlinx.android.synthetic.main.entry_history.view.*
import java.text.SimpleDateFormat
import java.util.*

class HistoryListAdapter(private val history: History, private val itemClick: (Entry) -> Unit) :
  RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  private val startTimesToRemove = mutableSetOf<Long>()

  private val mActionModeCallback by lazy {
    object : ActionMode.Callback {
      override fun onActionItemClicked(mode: ActionMode, item: MenuItem?) = when (item?.itemId) {
        R.id.delete -> {
          history.removeEntries(startTimesToRemove)
          mode.finish()
          true
        }
        else -> false
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
        itemView.entryStartTime.text = SimpleDateFormat("MMM dd HH:mm").format(Date(startTime * 1000))
        itemView.setOnClickListener {
          itemClick(this)
          adapter.notifyDataSetChanged()
        }
        itemView.setOnLongClickListener {
          (view.context as AppCompatActivity).startActionMode(mActionModeCallback)
          startTimesToRemove.clear()
          startTimesToRemove.add(startTime)
          true
        }
      }
    }
  }
}
