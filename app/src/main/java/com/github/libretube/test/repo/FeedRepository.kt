package com.github.libretube.test.repo

import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.db.obj.SubscriptionsFeedItem

data class FeedProgress(
    val currentProgress: Int,
    val total: Int
)

interface FeedRepository {
    suspend fun getFeed(
        forceRefresh: Boolean,
        onProgressUpdate: (FeedProgress) -> Unit
    ): List<StreamItem>
    suspend fun removeChannel(channelId: String) {}
    suspend fun submitFeedItemChange(feedItem: SubscriptionsFeedItem) {}
}
