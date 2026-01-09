package com.github.libretube.test.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.test.databinding.RecentHistoryItemBinding
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.adapters.callbacks.DiffUtilItemCallback
import androidx.core.os.bundleOf
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet

class RecentHistoryAdapter : ListAdapter<WatchHistoryItem, RecentHistoryAdapter.ViewHolder>(
    DiffUtilItemCallback(areItemsTheSame = { old, new -> old.videoId == new.videoId })
) {

    class ViewHolder(val binding: RecentHistoryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecentHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            title.text = item.title
            uploader.text = item.uploader
            ImageHelper.loadImage(item.thumbnailUrl, thumbnail)
            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, item.videoId)
            }
            root.setOnLongClickListener {
                VideoOptionsBottomSheet().apply {
                    arguments = bundleOf(
                        IntentData.streamItem to item.toStreamItem()
                    )
                }.show((root.context as BaseActivity).supportFragmentManager, VideoOptionsBottomSheet::class.java.name)
                true
            }
        }
    }
}
