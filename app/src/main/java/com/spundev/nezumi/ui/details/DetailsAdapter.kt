package com.spundev.nezumi.ui.details

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spundev.nezumi.databinding.RvFormatItemBinding
import com.spundev.nezumi.model.Format

class DetailsAdapter internal constructor(
        context: Context,
        val clickOpenOnBrowserListener: (url: String) -> Unit,
        val clickDownloadListener: (url: String) -> Unit,
        val longClickListener: (url: String) -> Unit
) : ListAdapter<Format, DetailsAdapter.FormatViewHolder>(object :
        DiffUtil.ItemCallback<Format>() {
    override fun areItemsTheSame(oldItem: Format, newItem: Format) = oldItem.itag == newItem.itag
    override fun areContentsTheSame(oldItem: Format, newItem: Format) = oldItem == newItem
}) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatViewHolder {
        val binding: RvFormatItemBinding = RvFormatItemBinding.inflate(inflater, parent, false)
        return FormatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FormatViewHolder, position: Int) =
            holder.bindTo(getItem(position))

    inner class FormatViewHolder(
            private val binding: RvFormatItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindTo(item: Format) {
            binding.formatTextView.text = item.description
            binding.openBrowserImageView.setOnClickListener { clickOpenOnBrowserListener(item.url) }
            binding.downloadImageView.setOnClickListener { clickDownloadListener(item.url) }
            binding.formatTextView.setOnLongClickListener {
                longClickListener(item.url)
                true
            }
        }
    }
}