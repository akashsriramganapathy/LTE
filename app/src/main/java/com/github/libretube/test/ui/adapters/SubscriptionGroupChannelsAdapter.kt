package com.github.libretube.test.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.github.libretube.test.api.obj.Subscription
import com.github.libretube.test.databinding.SubscriptionGroupChannelRowBinding
import com.github.libretube.test.db.obj.SubscriptionGroup
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.libretube.test.ui.viewholders.SubscriptionGroupChannelRowViewHolder

class SubscriptionGroupChannelsAdapter(
    private val group: SubscriptionGroup,
    private val onGroupChanged: (SubscriptionGroup) -> Unit
) : ListAdapter<Subscription, SubscriptionGroupChannelRowViewHolder>(DiffUtilItemCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionGroupChannelRowViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SubscriptionGroupChannelRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionGroupChannelRowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionGroupChannelRowViewHolder, position: Int) {
        val channel = getItem(holder.bindingAdapterPosition)
        holder.binding.apply {
            root.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, channel.url)
            }
            subscriptionChannelName.text = channel.name
            ImageHelper.loadImage(channel.avatar, subscriptionChannelImage, true)

            val channelId = channel.url.toID()
            channelIncluded.setOnCheckedChangeListener(null)
            channelIncluded.isChecked = group.channels.contains(channelId)
            channelIncluded.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) group.channels += channelId else group.channels -= channelId
                onGroupChanged(group)
            }
        }
    }
}

