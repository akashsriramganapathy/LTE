package com.github.libretube.test.ui.models

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.libretube.test.R
import android.net.Uri
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.enums.ImportFormat
import com.github.libretube.test.helpers.ImportHelper
import com.github.libretube.test.util.PlayingQueue
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

class PlaylistViewModel(
    val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = savedStateHandle.getStateFlow(UI_STATE, UiState())
    val uiState = _uiState.asLiveData()

    fun fetchPlaylists() {
        viewModelScope.launch {
            kotlin.runCatching {
                PlaylistsHelper.getPlaylists()
            }.onSuccess { playlists ->
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    playlists = playlists.filterNot { list -> list.name.isNullOrEmpty() }
                )
            }.onFailure {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.unknown_error)
                )
            }
        }
    }

    fun onAddToPlaylist(playlistIndex: Int) {
        val playlist = _uiState.value.playlists.getOrElse(playlistIndex) { return }
        savedStateHandle[UI_STATE] = _uiState.value.copy(lastSelectedPlaylistId = playlist.id)

        val videoInfo = savedStateHandle.get<StreamItem>(IntentData.videoInfo)
        val streams = videoInfo?.let { listOf(it) } ?: PlayingQueue.getStreams()

        viewModelScope.launch {
            runCatching {
                if (streams.isEmpty()) {
                    throw IllegalArgumentException()
                }
                PlaylistsHelper.addToPlaylist(playlist.id!!, *streams.toTypedArray())
            }.onSuccess {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.added_to_playlist, listOf(playlist.name!!)),
                    saved = Unit,
                )
            }
            .onFailure {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.unknown_error)
                )
            }
        }
    }

    fun playBackground(playlistId: String) {
        // Handled in UI for now by service launch, or we can add a one-shot event
        // Actually, usually we start the service from the UI context or a helper.
        // But let's assume we trigger a UI event to start it.
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            runCatching {
                PlaylistsHelper.deletePlaylist(playlistId)
            }.onSuccess {
                // Navigate back usually
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            runCatching {
                PlaylistsHelper.renamePlaylist(playlistId, newName)
            }.onSuccess {
                fetchPlaylists() // refresh
            }
        }
    }

    fun changeDescription(playlistId: String, newDescription: String) {
        viewModelScope.launch {
            runCatching {
                PlaylistsHelper.changePlaylistDescription(playlistId, newDescription)
            }.onSuccess {
                fetchPlaylists() // refresh
            }
        }
    }

    fun updateSortOrder(playlistId: String, order: String) {
        // TODO: Implement per-playlist sort persistence if needed, or global
        // For now, let's assume global preference or just sorting the list in memory?
        // The user wants "sort export playlist", so likely the view is sorted.
        // We might need to update the UI state with the new sort.
    }

    fun exportPlaylist(context: android.content.Context, playlistId: String, uri: Uri) {
        viewModelScope.launch {
            // Needs context for ContentResolver, usually passed from UI or injected.
            // Since we are in VM, passing context is bad practice but for 'export' helper it might be needed.
            // Better to delegate this to the UI/Fragment to call ImportHelper directly,
            // OR use a repository that has application context.
            // For now, let's keep it in UI/Fragment for simplicity as ImportHelper needs Context.
        }
    }

    fun onMessageShown() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(message = null)
    }

    fun onDismissed() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(saved = null)
    }

    @Parcelize
    data class UiState(
        val lastSelectedPlaylistId: String? = null,
        val playlists: List<Playlists> = emptyList(),
        val message: Message? = null,
        val saved: Unit? = null,
    ) : Parcelable {
        @Parcelize
        data class Message(
            @StringRes val resId: Int,
            val formatArgs: List<@RawValue Any>? = null,
        ) : Parcelable
    }

    companion object {
        private const val UI_STATE = "ui_state"

        val Factory = viewModelFactory {
            initializer {
                PlaylistViewModel(
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

