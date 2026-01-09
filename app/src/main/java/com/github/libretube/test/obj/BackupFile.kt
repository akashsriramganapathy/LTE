package com.github.libretube.test.obj


import com.github.libretube.test.db.obj.LocalPlaylistWithVideos
import com.github.libretube.test.db.obj.LocalSubscription

import com.github.libretube.test.db.obj.SearchHistoryItem
import com.github.libretube.test.db.obj.SubscriptionGroup
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.db.obj.WatchPosition
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BackupFile(
    //
    // some stuff for compatibility with external imports
    //
    val format: String = "LibreTube",
    val version: Int = 1,

    //
    // only compatible with LibreTube itself, database objects
    //
    var watchHistory: List<WatchHistoryItem>? = emptyList(),
    var watchPositions: List<WatchPosition>? = emptyList(),
    var searchHistory: List<SearchHistoryItem>? = emptyList(),

    var playlistBookmarks: List<PlaylistBookmark>? = emptyList(),

    //
    // Preferences, stored as a key value map
    //
    var preferences: List<PreferenceItem>? = emptyList(),

    //
    // Database objects with compatibility for external imports/exports
    //
    @JsonNames("groups", "channelGroups")
    var groups: List<SubscriptionGroup>? = emptyList(),

    @JsonNames("subscriptions", "localSubscriptions")
    var subscriptions: List<LocalSubscription>? = emptyList(),

    // playlists are exported in two different formats because the formats differ too much unfortunately
    var localPlaylists: List<LocalPlaylistWithVideos>? = emptyList(),

)

