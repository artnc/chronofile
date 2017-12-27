package com.chaidarun.chronofile

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class HistoryListAdapter(private val items: List<Entry>, private val itemClick: (Entry) -> Unit) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(TextView(parent.context), itemClick, this)

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindEntry(items[position])
  }

  override fun getItemCount() = items.size

  class ViewHolder(private val textView: TextView, private val itemClick: (Entry) -> Unit, private val adapter: HistoryListAdapter) : RecyclerView.ViewHolder(textView) {
    fun bindEntry(entry: Entry) {
      with(entry) {
        val date = SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(Date(startTime * 1000))
        textView.text = "$date $activity"
        textView.setOnClickListener {
          itemClick(this)
          adapter.notifyDataSetChanged()
        }
      }
    }
  }
}
