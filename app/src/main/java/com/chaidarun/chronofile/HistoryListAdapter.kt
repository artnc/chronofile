package com.chaidarun.chronofile

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.jetbrains.anko.find
import java.text.SimpleDateFormat
import java.util.*

class HistoryListAdapter(private val items: List<Entry>, private val itemClick: (Entry) -> Unit) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.history_entry, parent, false), itemClick, this)

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindEntry(items[position])
  }

  override fun getItemCount() = items.size

  class ViewHolder(view: View, private val itemClick: (Entry) -> Unit, private val adapter: HistoryListAdapter) : RecyclerView.ViewHolder(view) {

    private val textView = view.find<TextView>(R.id.activity)

    fun bindEntry(entry: Entry) {
      with(entry) {
        val date = SimpleDateFormat("MMM dd HH:mm").format(Date(startTime * 1000))
        textView.text = "$date $activity"
        textView.setOnClickListener {
          itemClick(this)
          adapter.notifyDataSetChanged()
        }
      }
    }
  }
}
