package com.spundev.nezumi.ui.details

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spundev.nezumi.databinding.RvFormatHeaderBinding
import com.spundev.nezumi.model.VideoDetails

class DetailsHeaderAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<DetailsHeaderAdapter.HeaderViewHolder>() {

    var headerContent: VideoDetails? = null

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding: RvFormatHeaderBinding =
            RvFormatHeaderBinding.inflate(inflater, parent, false)
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(headerContent)
    }

    override fun getItemCount(): Int {
        return 1
    }

    fun setContent(newContent: VideoDetails) {
        headerContent = newContent
        notifyItemChanged(0)
    }

    inner class HeaderViewHolder(
        private val binding: RvFormatHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        var currentItem: VideoDetails? = null

        fun bind(item: VideoDetails?) {
            currentItem = item
            if (item != null) {
                binding.titleTextView.text = item.title
                binding.channelTextView.text = item.author
            } else {
                binding.titleTextView.text = ""
                binding.channelTextView.text = ""
            }
        }
    }
}