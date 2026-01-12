package com.github.libretube.test.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.api.obj.Playlist
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.enums.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistScreenModel : ViewModel() {
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist = _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

    fun loadPlaylist(playlistId: String, type: PlaylistType) {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedPlaylist = withContext(Dispatchers.IO) {
                runCatching { PlaylistsHelper.getPlaylist(playlistId) }.getOrNull()
            }
            _playlist.value = fetchedPlaylist
            
            if (type == PlaylistType.PUBLIC) {
                _isBookmarked.value = withContext(Dispatchers.IO) {
                    DatabaseHolder.Database.playlistBookmarkDao().includes(playlistId)
                }
            }
            _isLoading.value = false
        }
    }

    fun toggleBookmark(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _isBookmarked.value
            if (current) {
                DatabaseHolder.Database.playlistBookmarkDao().deleteById(playlistId)
            } else {
                _playlist.value?.let { p ->
                    DatabaseHolder.Database.playlistBookmarkDao().insert(p.toPlaylistBookmark(playlistId))
                }
            }
            _isBookmarked.value = !current
        }
    }

    fun saveReorder(playlistId: String, items: List<StreamItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            PlaylistsHelper.updatePlaylistVideos(playlistId, items)
            _playlist.value = _playlist.value?.copy(relatedStreams = items)
        }
    }

    fun deleteVideo(playlistId: String, item: StreamItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentStreams = _playlist.value?.relatedStreams?.toMutableList() ?: return@launch
            val index = currentStreams.indexOf(item)
            if (index != -1) {
                PlaylistsHelper.removeFromPlaylist(playlistId, index)
                currentStreams.removeAt(index)
                _playlist.value = _playlist.value?.copy(relatedStreams = currentStreams)
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            PlaylistsHelper.renamePlaylist(playlistId, newName)
            _playlist.value = _playlist.value?.copy(name = newName)
        }
    }

    fun changeDescription(playlistId: String, newDescription: String) {
        viewModelScope.launch(Dispatchers.IO) {
            PlaylistsHelper.changePlaylistDescription(playlistId, newDescription)
            _playlist.value = _playlist.value?.copy(description = newDescription)
        }
    }

    fun deletePlaylist(playlistId: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            PlaylistsHelper.deletePlaylist(playlistId)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }
}
