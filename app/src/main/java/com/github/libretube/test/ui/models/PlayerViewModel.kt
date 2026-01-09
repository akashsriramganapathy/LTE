package com.github.libretube.test.ui.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.github.libretube.test.api.obj.Segment
import com.github.libretube.test.api.obj.Subtitle
import com.github.libretube.test.helpers.PlayerHelper
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@UnstableApi
class PlayerViewModel : ViewModel() {
    
    private val _expandPlayerTrigger = Channel<Unit>(Channel.BUFFERED)
    val expandPlayerTrigger = _expandPlayerTrigger.receiveAsFlow()

    fun triggerPlayerExpansion() {
        _expandPlayerTrigger.trySend(Unit)
    }
    
    private val _playerController = MutableStateFlow<MediaController?>(null)
    val playerController = _playerController.asStateFlow()

    fun setPlayerController(controller: MediaController?) {
        _playerController.value = controller
    }

    // var playerController: MediaController? = null // Removed

    fun togglePlayPause() {
        playerController.value?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        playerController.value?.seekTo(position)
    }

    fun skipPrevious() {
        playerController.value?.seekToPrevious()
    }

    fun skipNext() {
        playerController.value?.seekToNext()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    
    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()
    
    private val _uploader = MutableStateFlow("")
    val uploader = _uploader.asStateFlow()

    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        _isPlaying.value = isPlaying
        _currentPosition.value = position
        _duration.value = duration
    }

    fun updateMetadata(title: String, uploader: String) {
        _title.value = title
        _uploader.value = uploader
    }

    var segments = MutableLiveData<List<Segment>>()
    // this is only used to restore the subtitle after leaving PiP, the actual caption state
    // should always be read from the player's selected tracks!
    var currentSubtitle = Subtitle(code = PlayerHelper.defaultSubtitleCode)
    var sponsorBlockConfig = PlayerHelper.getSponsorBlockCategories()

    /**
     * Whether an orientation change is in progress, so that the current player should be continued to use
     *
     * Set to true if the activity will be recreated due to an orientation change
     */
    var isOrientationChangeInProgress = false

    val deArrowData = MutableLiveData<com.github.libretube.test.api.obj.DeArrowContent?>()

    fun fetchDeArrowData(videoId: String) {
        if (!com.github.libretube.test.helpers.PreferenceHelper.getBoolean(com.github.libretube.test.constants.PreferenceKeys.DEARROW, true)) {
             deArrowData.postValue(null)
             return
        }
        android.util.Log.d("DeArrowPlayer", "fetchDeArrowData called for $videoId")
        
        viewModelScope.launch {
            try {
                android.util.Log.d("DeArrowPlayer", "Fetching DeArrow data for $videoId")
                val response = com.github.libretube.test.api.MediaServiceRepository.instance.getDeArrowContent(videoId)
                if (response != null && (response.titles.isNotEmpty() || response.thumbnails.isNotEmpty())) {
                    android.util.Log.d("DeArrowPlayer", "SUCCESS: Received DeArrow data for $videoId. Titles: ${response.titles.size}, Thumbs: ${response.thumbnails.size}")
                } else {
                    android.util.Log.d("DeArrowPlayer", "NO DATA: No DeArrow curation for $videoId")
                }
                deArrowData.postValue(response)
            } catch (e: Exception) {
                android.util.Log.e("DeArrowPlayer", "ERROR: Could not fetch DeArrow data for $videoId", e)
                deArrowData.postValue(null)
            }
        }
    }
}
