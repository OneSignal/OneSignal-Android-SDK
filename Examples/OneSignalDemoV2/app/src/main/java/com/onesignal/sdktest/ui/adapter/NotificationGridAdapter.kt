package com.onesignal.sdktest.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.databinding.ItemNotificationGridBinding

class NotificationGridAdapter(
    private val items: List<NotificationType>,
    private val onItemClick: (NotificationType) -> Unit
) : RecyclerView.Adapter<NotificationGridAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemNotificationGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationType) {
            binding.tvTitle.text = item.title
            binding.ivIcon.setImageResource(item.iconResId)
            binding.itemContainer.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
