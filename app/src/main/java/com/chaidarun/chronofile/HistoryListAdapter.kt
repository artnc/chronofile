package com.chaidarun.chronofile

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.history_entry.view.*
import java.text.SimpleDateFormat
import java.util.*

class HistoryListAdapter(private val items: List<Entry>, private val itemClick: (Entry) -> Unit) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.history_entry, parent, false), itemClick, this)

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindEntry(items[position])
  }

  override fun getItemCount() = items.size

  class ViewHolder(view: View, private val itemClick: (Entry) -> Unit, private val adapter: HistoryListAdapter) : RecyclerView.ViewHolder(view) {

    fun bindEntry(entry: Entry) {
      with(entry) {
        val date = SimpleDateFormat("MMM dd HH:mm").format(Date(startTime * 1000))
        itemView.activity.text = "$date $activity"
        itemView.setOnClickListener {
          itemClick(this)
          adapter.notifyDataSetChanged()
        }
      }
    }
  }
}
