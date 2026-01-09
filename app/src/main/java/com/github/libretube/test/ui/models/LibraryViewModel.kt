package com.github.libretube.test.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.db.DatabaseHelper
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.constants.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    

    val historyItems = MutableLiveData<List<WatchHistoryItem>>(emptyList())
    val historyCount = MutableLiveData(0)
    val downloadCount = MutableLiveData(0)
    val playlists = MutableLiveData<List<Playlists>>(emptyList())
    val bookmarks = MutableLiveData<List<PlaylistBookmark>>(emptyList())
    val isRefreshing = MutableLiveData(false)

    fun refreshData() {
        isRefreshing.value = true
        viewModelScope.launch {
            val historyLatest = withContext(Dispatchers.IO) {
                DatabaseHelper.getWatchHistoryPage(1, 10)
            }
            historyItems.postValue(historyLatest)

            val totalHistorySize = withContext(Dispatchers.IO) {
                DatabaseHolder.Database.watchHistoryDao().getSize()
            }
            historyCount.postValue(totalHistorySize)

            val downloads = withContext(Dispatchers.IO) {
                val db = DatabaseHolder.Database
                val cursor = db.query("SELECT COUNT(*) FROM download", null)
                var count = 0
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
                cursor.close()
                count
            }
            downloadCount.postValue(downloads)

            val pLists = withContext(Dispatchers.IO) {
                try {
                    PlaylistsHelper.getPlaylists()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            
            val bMarks = withContext(Dispatchers.IO) {
                val rawBookmarks = DatabaseHolder.Database.playlistBookmarkDao().getAll()
                val bookmarks = com.github.libretube.test.util.DeArrowUtil.deArrowPlaylistBookmarks(rawBookmarks)
                
                when (com.github.libretube.test.helpers.PreferenceHelper.getString(
                    com.github.libretube.test.constants.PreferenceKeys.PLAYLISTS_ORDER, 
                    "creation_date"
                )) {
                    "alphabetic" -> bookmarks.sortedBy { it.playlistName?.lowercase() }
                    "alphabetic_reversed" -> bookmarks.sortedByDescending { it.playlistName?.lowercase() }
                    "creation_date" -> bookmarks.sortedBy { it.savedAt }
                    "creation_date_reversed" -> bookmarks.sortedByDescending { it.savedAt }
                    "custom" -> bookmarks.sortedBy { it.orderIndex }
                    else -> bookmarks
                }
            }

            playlists.postValue(pLists)
            bookmarks.postValue(bMarks)
            isRefreshing.postValue(false)
            
            // Sync bookmarks in background to update video counts/thumbnails
            // This runs after the initial load to keep the UI responsive
            launch(Dispatchers.IO) {
                try {
                    val updatedBookmarks = bMarks.mapNotNull { bookmark ->
                        try {
                            // Only update remote playlists, not local ones if they somehow got here
                            if (!bookmark.playlistId.all { it.isDigit() }) {
                                com.github.libretube.test.api.MediaServiceRepository.instance.getPlaylist(bookmark.playlistId).let { remotePlaylist ->
                                    bookmark.copy(
                                        playlistName = remotePlaylist.name ?: bookmark.playlistName,
                                        thumbnailUrl = remotePlaylist.thumbnailUrl ?: bookmark.thumbnailUrl,
                                        videos = remotePlaylist.videos,
                                        uploader = remotePlaylist.uploader ?: bookmark.uploader,
                                        uploaderUrl = remotePlaylist.uploaderUrl ?: bookmark.uploaderUrl,
                                        uploaderAvatar = remotePlaylist.uploaderAvatar ?: bookmark.uploaderAvatar
                                    )
                                }
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null // Skip failed updates
                        }
                    }

                     if (updatedBookmarks.isNotEmpty()) {
                         DatabaseHolder.Database.playlistBookmarkDao().insertAll(updatedBookmarks)
                         val rawBookmarks = DatabaseHolder.Database.playlistBookmarkDao().getAll()
                         val refreshedBookmarks = com.github.libretube.test.util.DeArrowUtil.deArrowPlaylistBookmarks(rawBookmarks)
                         
                         val sortedBookmarks = when (PreferenceHelper.getString(PreferenceKeys.PLAYLISTS_ORDER, "creation_date")) {
                            "alphabetic" -> refreshedBookmarks.sortedBy { it.playlistName?.lowercase() }
                            "alphabetic_reversed" -> refreshedBookmarks.sortedByDescending { it.playlistName?.lowercase() }
                            "creation_date" -> refreshedBookmarks.sortedBy { it.savedAt }
                            "creation_date_reversed" -> refreshedBookmarks.sortedByDescending { it.savedAt }
                            "custom" -> refreshedBookmarks.sortedBy { it.orderIndex }
                            else -> refreshedBookmarks
                         }
                         bookmarks.postValue(sortedBookmarks)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
