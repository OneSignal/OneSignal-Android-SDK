package com.onesignal.sdktest.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.onesignal.sdktest.databinding.ItemPairBinding

class PairListAdapter(
    private val onRemoveClick: (String) -> Unit
) : ListAdapter<Pair<String, String>, PairListAdapter.ViewHolder>(PairDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPairBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPairBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<String, String>) {
            binding.tvKey.text = item.first
            binding.tvValue.text = item.second
            binding.btnRemove.setOnClickListener {
                onRemoveClick(item.first)
            }
        }
    }

    class PairDiffCallback : DiffUtil.ItemCallback<Pair<String, String>>() {
        override fun areItemsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem.first == newItem.first
        }

        override fun areContentsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem == newItem
        }
    }
}
