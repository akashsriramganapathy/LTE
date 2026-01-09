package com.github.libretube.test.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val _historyItems = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val historyItems = _historyItems.asStateFlow()

    private val _historyCount = MutableStateFlow(0)
    val historyCount = _historyCount.asStateFlow()

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount = _downloadCount.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlists>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<PlaylistBookmark>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode = _isReorderMode.asStateFlow()

    private val _reorderPlaylists = MutableStateFlow<List<Playlists>>(emptyList())
    val reorderPlaylists = _reorderPlaylists.asStateFlow()

    private val _reorderBookmarks = MutableStateFlow<List<PlaylistBookmark>>(emptyList())
    val reorderBookmarks = _reorderBookmarks.asStateFlow()

    fun refreshData() {
        _isRefreshing.value = true
        viewModelScope.launch {
            val historyLatest = withContext(Dispatchers.IO) {
                DatabaseHelper.getWatchHistoryPage(1, 10)
            }
            _historyItems.value = historyLatest

            val totalHistorySize = withContext(Dispatchers.IO) {
                DatabaseHolder.Database.watchHistoryDao().getSize()
            }
            _historyCount.value = totalHistorySize

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
            _downloadCount.value = downloads

            val pLists = withContext(Dispatchers.IO) {
                try {
                    PlaylistsHelper.getPlaylists()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _playlists.value = pLists

            val bMarks = withContext(Dispatchers.IO) {
                val rawBookmarks = DatabaseHolder.Database.playlistBookmarkDao().getAll()
                val bookmarks = com.github.libretube.test.util.DeArrowUtil.deArrowPlaylistBookmarks(rawBookmarks)
                
                when (PreferenceHelper.getString(PreferenceKeys.PLAYLISTS_ORDER, "creation_date")) {
                    "alphabetic" -> bookmarks.sortedBy { it.playlistName?.lowercase() }
                    "alphabetic_reversed" -> bookmarks.sortedByDescending { it.playlistName?.lowercase() }
                    "creation_date" -> bookmarks.sortedBy { it.savedAt }
                    "creation_date_reversed" -> bookmarks.sortedByDescending { it.savedAt }
                    "custom" -> bookmarks.sortedBy { it.orderIndex }
                    else -> bookmarks
                }
            }
            _bookmarks.value = bMarks

            _isRefreshing.value = false
            
            // Sync bookmarks in background
            launch(Dispatchers.IO) {
                try {
                    val updatedBookmarks = bMarks.mapNotNull { bookmark ->
                        try {
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
                            null
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
                          _bookmarks.value = sortedBookmarks
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun toggleReorderMode(initialListPlaylists: List<Playlists>? = null, initialListBookmarks: List<PlaylistBookmark>? = null) {
        _isReorderMode.value = !_isReorderMode.value
        if (_isReorderMode.value) {
            _reorderPlaylists.value = initialListPlaylists ?: _playlists.value
            _reorderBookmarks.value = initialListBookmarks ?: _bookmarks.value
        }
    }

    fun onItemMovePlaylists(from: Int, to: Int) {
        val list = _reorderPlaylists.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _reorderPlaylists.value = list
        }
    }

    fun onItemMoveBookmarks(from: Int, to: Int) {
        val list = _reorderBookmarks.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _reorderBookmarks.value = list
        }
    }

    fun saveReorder(isPlaylists: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isReorderMode.value = false
            if (isPlaylists) {
                val ids = _reorderPlaylists.value.mapNotNull { it.id }
                PlaylistsHelper.updateLocalPlaylistOrder(ids)
            } else {
                val ids = _reorderBookmarks.value.map { it.playlistId }
                PlaylistsHelper.updateBookmarkOrder(ids)
            }
            PreferenceHelper.putString(PreferenceKeys.PLAYLISTS_ORDER, "custom")
            // refreshData() will be called automatically or manually
            refreshData()
        }
    }
}
