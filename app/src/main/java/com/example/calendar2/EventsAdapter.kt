package com.example.calendar2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calendar2.data.Event
import com.example.calendar2.databinding.ItemEventBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventsAdapter : ListAdapter<Event, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    var onItemClick: ((Event) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(event)
        }
    }

    class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: Event) {
            binding.eventTitle.text = event.title
            
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val start = Instant.ofEpochMilli(event.startTime).atZone(ZoneId.systemDefault())
            val end = Instant.ofEpochMilli(event.endTime).atZone(ZoneId.systemDefault())
            
            if (event.isAllDay) {
                binding.eventTime.text = binding.root.context.getString(R.string.text_all_day)
            } else {
                binding.eventTime.text = "${formatter.format(start)} - ${formatter.format(end)}"
            }
            
            if (!event.location.isNullOrEmpty()) {
                binding.eventLocation.visibility = android.view.View.VISIBLE
                binding.eventLocation.text = event.location
            } else {
                binding.eventLocation.visibility = android.view.View.GONE
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }
}
