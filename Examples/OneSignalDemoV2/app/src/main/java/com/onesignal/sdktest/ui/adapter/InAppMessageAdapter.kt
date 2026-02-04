package com.onesignal.sdktest.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.onesignal.sdktest.data.model.InAppMessageType
import com.onesignal.sdktest.databinding.ItemNotificationGridBinding

class InAppMessageAdapter(
    private val items: List<InAppMessageType>,
    private val onItemClick: (InAppMessageType) -> Unit
) : RecyclerView.Adapter<InAppMessageAdapter.ViewHolder>() {

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

        fun bind(item: InAppMessageType) {
            binding.tvTitle.text = item.title
            binding.ivIcon.setImageResource(item.iconResId)
            binding.itemContainer.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
