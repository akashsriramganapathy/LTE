package com.github.libretube.test.api

import com.github.libretube.test.api.obj.Subscription
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.obj.SubscriptionsFeedItem
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.repo.FeedProgress
import com.github.libretube.test.repo.FeedRepository
import com.github.libretube.test.repo.LocalFeedRepository

object SubscriptionHelper {
    /**
     * The maximum number of channel IDs that can be passed via a GET request for fetching
     * the subscriptions list and the feed
     */
    const val GET_SUBSCRIPTIONS_LIMIT = 100

    private val localFeedExtraction
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.LOCAL_FEED_EXTRACTION,
            false
        )
    private val subscriptionsRepository: com.github.libretube.test.repo.SubscriptionsRepository
        get() = com.github.libretube.test.repo.LocalSubscriptionsRepository()

    private val feedRepository: FeedRepository
        get() = LocalFeedRepository()

    suspend fun subscribe(
        channelId: String, name: String, uploaderAvatar: String?, verified: Boolean
    ) = subscriptionsRepository.subscribe(channelId, name, uploaderAvatar, verified)

    suspend fun unsubscribe(channelId: String) {
        subscriptionsRepository.unsubscribe(channelId)
        // remove videos from (local) feed
        feedRepository.removeChannel(channelId)
    }
    suspend fun isSubscribed(channelId: String) = subscriptionsRepository.isSubscribed(channelId)
    suspend fun importSubscriptions(newChannels: List<String>) =
        subscriptionsRepository.importSubscriptions(newChannels)

    suspend fun getSubscriptions() =
        subscriptionsRepository.getSubscriptions().sortedBy { it.name.lowercase() }

    suspend fun getSubscriptionChannelIds() = subscriptionsRepository.getSubscriptionChannelIds()
    suspend fun getFeed(forceRefresh: Boolean, onProgressUpdate: (FeedProgress) -> Unit = {}) =
        com.github.libretube.test.util.DeArrowUtil.deArrowStreamItems(feedRepository.getFeed(forceRefresh, onProgressUpdate))

    suspend fun submitFeedItemChange(feedItem: SubscriptionsFeedItem) =
        feedRepository.submitFeedItemChange(feedItem)

    suspend fun submitSubscriptionChannelInfosChanged(subscriptions: List<Subscription>) =
        subscriptionsRepository.submitSubscriptionChannelInfosChanged(subscriptions)
}
