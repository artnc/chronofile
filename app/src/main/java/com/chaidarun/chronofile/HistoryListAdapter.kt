package com.chaidarun.chronofile

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class HistoryListAdapter(private val items: List<Entry>) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(TextView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (startTime, activity) = items[position]
        val date = SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(Date(startTime * 1000))
        holder.textView.text = "$date $activity"
    }

    override fun getItemCount() = items.size

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}