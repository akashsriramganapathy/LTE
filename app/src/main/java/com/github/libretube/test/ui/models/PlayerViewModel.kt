package com.github.libretube.test.ui.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackParameters
import com.github.libretube.test.api.obj.Segment
import com.github.libretube.test.api.obj.Subtitle
import com.github.libretube.test.helpers.PlayerHelper
import com.github.libretube.test.extensions.toID
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.libretube.test.api.MediaServiceRepository
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.libretube.test.api.SubscriptionHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

import com.github.libretube.test.util.PlayingQueue
import com.github.libretube.test.api.obj.ChapterSegment
import com.github.libretube.test.api.obj.StreamItem

sealed class PlayerCommandEvent {
    object Share : PlayerCommandEvent()
    object Download : PlayerCommandEvent()
    object SaveToPlaylist : PlayerCommandEvent()
    object Bookmark : PlayerCommandEvent()
    object Subscribe : PlayerCommandEvent()
}

@UnstableApi
class PlayerViewModel : ViewModel() {
    
    private val _expandPlayerTrigger = Channel<Unit>(Channel.BUFFERED)
    val expandPlayerTrigger = _expandPlayerTrigger.receiveAsFlow()

    fun triggerPlayerExpansion() {
        _expandPlayerTrigger.trySend(Unit)
    }

    private val _collapsePlayerTrigger = Channel<Unit>(Channel.BUFFERED)
    val collapsePlayerTrigger = _collapsePlayerTrigger.receiveAsFlow()

    fun triggerPlayerCollapse() {
        _collapsePlayerTrigger.trySend(Unit)
    }

    private val _playVideoTrigger = Channel<StreamItem>(Channel.BUFFERED)
    val playVideoTrigger = _playVideoTrigger.receiveAsFlow()

    private val _playerCommandTrigger = kotlinx.coroutines.flow.MutableSharedFlow<PlayerCommandEvent>()
    val playerCommandTrigger = _playerCommandTrigger.asSharedFlow()

    fun triggerPlayerCommand(command: PlayerCommandEvent) {
        viewModelScope.launch {
            _playerCommandTrigger.emit(command)
        }
    }
    
    private val _playerController = MutableStateFlow<MediaController?>(null)
    val playerController = _playerController.asStateFlow()

    private var pendingMediaItem: androidx.media3.common.MediaItem? = null
    private var pendingPlayWhenReady: Boolean = false

    init {
        viewModelScope.launch {
            playerController.collect { controller ->
                if (controller != null && pendingVideoId != null) {
                    android.util.Log.d("PlayerViewModel", "PlayerController connected, executing pending playback command")
                    val args = android.os.Bundle().apply {
                        putString(com.github.libretube.test.enums.PlayerCommand.PLAY_VIDEO_BY_ID.name, pendingVideoId)
                    }
                    val command = androidx.media3.session.SessionCommand(
                        "run_player_command_action",
                        android.os.Bundle.EMPTY
                    )
                    controller.sendCustomCommand(command, args)
                    if (pendingPlayWhenReady) controller.play()
                    pendingVideoId = null
                }
            }
        }
    }

    fun setPlayerController(controller: MediaController?) {
        _playerController.value = controller
    }

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

    private val _uploaderAvatar = MutableStateFlow<String?>(null)
    val uploaderAvatar = _uploaderAvatar.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _views = MutableStateFlow(0L)
    val views = _views.asStateFlow()

    private val _likes = MutableStateFlow(0L)
    val likes = _likes.asStateFlow()

    private val _subscriberCount = MutableStateFlow<Long?>(null)
    val subscriberCount = _subscriberCount.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed = _isSubscribed.asStateFlow()

    private val _dominantColor = MutableStateFlow(Color.Transparent)
    val dominantColor = _dominantColor.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked = _isLiked.asStateFlow()

    private val _isDisliked = MutableStateFlow(false)
    val isDisliked = _isDisliked.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded = _isExpanded.asStateFlow()

    private val _currentStream = MutableStateFlow<StreamItem?>(null)
    val currentStream = _currentStream.asStateFlow()

    private val _isInPip = MutableStateFlow(false)
    val isInPip = _isInPip.asStateFlow()

    fun setIsInPip(inPip: Boolean) {
        _isInPip.value = inPip
    }

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen = _isFullscreen.asStateFlow()

    private val _fullscreenTrigger = MutableSharedFlow<Boolean>()
    val fullscreenTrigger = _fullscreenTrigger.asSharedFlow()

    fun toggleFullscreen() {
        viewModelScope.launch {
            val newState = !_isFullscreen.value
            _isFullscreen.value = newState
            _fullscreenTrigger.emit(newState)
        }
    }

    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    fun toggleControlsLock() {
        _areControlsLocked.value = !_areControlsLocked.value
    }

    private val _showControls = MutableStateFlow(true)
    val showControls = _showControls.asStateFlow()

    fun toggleControls() {
        if (_areControlsLocked.value) return // Don't toggle if locked
        _showControls.value = !_showControls.value
    }

    fun setControlsVisible(visible: Boolean) {
        if (_areControlsLocked.value && visible) return
        _showControls.value = visible
    }

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch = _playbackPitch.asStateFlow()

    private val _repeatMode = MutableStateFlow(androidx.media3.common.Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _resizeMode = MutableStateFlow(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    private val _isAudioOnlyMode = MutableStateFlow(false)
    val isAudioOnlyMode = _isAudioOnlyMode.asStateFlow()

    fun toggleAudioOnlyMode() {
        _isAudioOnlyMode.value = !_isAudioOnlyMode.value
    }

    // Queue (direct from singleton)
    val queue = PlayingQueue.queueState

    // Chapters
    private val _chapters = MutableStateFlow<List<ChapterSegment>>(emptyList())
    val chapters = _chapters.asStateFlow()

    // Related Videos
    private val _relatedVideos = MutableStateFlow<List<StreamItem>>(emptyList())
    val relatedVideos = _relatedVideos.asStateFlow()

    // Comments (Using a simplified list for now, can expand later)
    private val _comments = MutableStateFlow<List<com.github.libretube.test.api.obj.Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _showCommentsSheet = MutableStateFlow(false)
    val showCommentsSheet = _showCommentsSheet.asStateFlow()

    fun openCommentsSheet() {
        _showCommentsSheet.value = true
    }

    fun closeCommentsSheet() {
        _showCommentsSheet.value = false
    }

    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        _isPlaying.value = isPlaying
        _currentPosition.value = position
        _playbackPosition.value = position
        _duration.value = duration
    }

    fun updateMetadata(
        title: String,
        uploader: String,
        uploaderAvatar: String? = null,
        description: String = "",
        views: Long = 0,
        likes: Long = 0,
        subscriberCount: Long? = null
    ) {
        _title.value = title
        _uploader.value = uploader
        _uploaderAvatar.value = uploaderAvatar
        _description.value = description
        _views.value = views
        _likes.value = likes
        _subscriberCount.value = subscriberCount
        _isBuffering.value = false // Metadata loaded, usually stop buffering indicator
        checkSubscriptionStatus()
        checkBookmarkStatus()
    }

    private fun checkBookmarkStatus() {
        val stream = _currentStream.value ?: return
        val videoId = stream.url?.toID() ?: return
        viewModelScope.launch {
            // Check if video is in any local playlist or bookmarked
            // For now, let's just check if it's in the "Favorites" or a special bookmarked list if we have one.
            // Actually, "Bookmark" in this context often refers to a simple local 'saved' state.
            // Let's use a simple preference or DB for 'Bookmarked' videos.
        }
    }
    
    fun toggleLike() {
        _isLiked.value = !_isLiked.value
        if (_isLiked.value) _isDisliked.value = false
    }

    fun toggleDislike() {
        _isDisliked.value = !_isDisliked.value
        if (_isDisliked.value) _isLiked.value = false
    }

    fun toggleBookmark() {
        _isBookmarked.value = !_isBookmarked.value
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    private fun checkSubscriptionStatus() {
        val stream = _currentStream.value ?: return
        val channelId = stream.uploaderUrl?.toID() ?: return
        viewModelScope.launch {
            _isSubscribed.value = SubscriptionHelper.isSubscribed(channelId) ?: false
        }
    }

    fun toggleSubscription() {
        val stream = _currentStream.value ?: return
        val channelId = stream.uploaderUrl?.toID() ?: return
        val name = stream.uploaderName ?: return
        
        viewModelScope.launch {
            if (_isSubscribed.value) {
                SubscriptionHelper.unsubscribe(channelId)
            } else {
                SubscriptionHelper.subscribe(
                    channelId = channelId,
                    name = name,
                    uploaderAvatar = stream.uploaderAvatar,
                    verified = stream.uploaderVerified ?: false
                )
            }
            _isSubscribed.value = !_isSubscribed.value
        }
    }
    fun updateBufferingState(isBuffering: Boolean) {
        _isBuffering.value = isBuffering
    }

    fun setStream(stream: StreamItem) {
        _currentStream.value = stream
        _title.value = stream.title ?: ""
        _uploader.value = stream.uploaderName ?: ""
        // Fetch dominant color
        extractDominantColor(stream.thumbnail)
    }

    fun updateDominantColor(color: Color) {
        _dominantColor.value = color
    }

    private fun extractDominantColor(url: String?) {
        if (url == null) {
            _dominantColor.value = Color.Transparent
            return
        }
        viewModelScope.launch {
            try {
                // This assumes we have a context, we can get it from an ImageLoader if it's singleton or passed?
                // Actually, let's keep it simple for now and just use a placeholder if we can't easily get context here.
                // Better: The UI will pass the context or we use a global Coil ImageLoader.
            } catch (e: Exception) {
                _dominantColor.value = Color.Transparent
            }
        }
    }
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerController.value?.let { player ->
            player.playbackParameters = PlaybackParameters(speed, _playbackPitch.value)
        }
        // Persist to preferences
        com.github.libretube.test.helpers.PreferenceHelper.putString(
            com.github.libretube.test.constants.PreferenceKeys.PLAYBACK_SPEED,
            speed.toString()
        )
    }

    fun setPlaybackPitch(pitch: Float) {
        _playbackPitch.value = pitch
        playerController.value?.let { player ->
            player.playbackParameters = PlaybackParameters(_playbackSpeed.value, pitch)
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
            androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        playerController.value?.repeatMode = nextMode
    }

    fun cycleResizeMode() {
        val nextMode = when (_resizeMode.value) {
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _resizeMode.value = nextMode
    }

    fun initializePlaybackSpeed() {
        val savedSpeed = com.github.libretube.test.helpers.PreferenceHelper.getString(
            com.github.libretube.test.constants.PreferenceKeys.PLAYBACK_SPEED,
            "1.0"
        ).toFloatOrNull() ?: 1.0f
        _playbackSpeed.value = savedSpeed
    }
    
    fun onQueueItemClicked(item: StreamItem) {
        _playVideoTrigger.trySend(item)
    }

    fun updateChapters(newChapters: List<ChapterSegment>) {
        _chapters.value = newChapters
    }

    fun updateRelatedVideos(videos: List<StreamItem>) {
        _relatedVideos.value = videos
    }

    fun updateComments(newComments: List<com.github.libretube.test.api.obj.Comment>) {
        _comments.value = newComments
    }

    var segments = MutableLiveData<List<Segment>>()
    // this is only used to restore the subtitle after leaving PiP, the actual caption state
    // should always be read from the player's selected tracks!
    var currentSubtitle = Subtitle(code = PlayerHelper.defaultSubtitleCode)
    var sponsorBlockConfig = PlayerHelper.getSponsorBlockCategories()

    private var pendingVideoId: String? = null

    fun loadVideo(
        videoId: String,
        playlistId: String? = null,
        channelId: String? = null,
        timestamp: Long = 0,
        playWhenReady: Boolean = true
    ) {
        android.util.Log.d("PlayerViewModel", "loadVideo called: videoId=$videoId")
        
        viewModelScope.launch {
           try {
                // Update queue immediately with placeholder for UI feedback
                val placeholderStream = StreamItem(
                    url = videoId.toID(),
                    title = "Loading...",
                    uploaderName = "",
                    thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    type = com.github.libretube.test.api.obj.StreamItem.TYPE_STREAM
                )
                // We update the local queue UI, but the Service will manage the actual queue via the command
                PlayingQueue.updateQueue(placeholderStream, playlistId, channelId)

                // Delegate playback to the Service via Controller Command
                val controller = _playerController.value
                if (controller != null) {
                    val args = android.os.Bundle().apply {
                        putString(com.github.libretube.test.enums.PlayerCommand.PLAY_VIDEO_BY_ID.name, videoId)
                    }
                    val command = androidx.media3.session.SessionCommand(
                        "run_player_command_action",
                        android.os.Bundle.EMPTY
                    )
                    controller.sendCustomCommand(command, args)
                    if (playWhenReady) controller.play()
                } else {
                    android.util.Log.w("PlayerViewModel", "PlayerController is null, queuing playback command")
                    pendingVideoId = videoId
                    pendingPlayWhenReady = playWhenReady
                }
                
                // Fetch full metadata for UI (handled independently of playback)
                val streams = withContext(kotlinx.coroutines.Dispatchers.IO) {
                   MediaServiceRepository.instance.getStreams(videoId)
                }
                
                val streamItem = streams.toStreamItem(videoId)
                setStream(streamItem)
                updateMetadata(
                    title = streams.title,
                    uploader = streams.uploader,
                    uploaderAvatar = streams.uploaderAvatar,
                    description = streams.description,
                    views = streams.views,
                    likes = streams.likes,
                    subscriberCount = streams.uploaderSubscriberCount
                )
                
                // Restore Suggestion Videos
                updateRelatedVideos(streams.relatedStreams)
                
                 // Update queue with real data for UI
                PlayingQueue.updateCurrent(streamItem)
                
                // Fetch Comments
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val commentsResponse = MediaServiceRepository.instance.getComments(videoId)
                        updateComments(commentsResponse.comments)
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerViewModel", "Error fetching comments", e)
                    }
                }

           } catch (e: Exception) {
               android.util.Log.e("PlayerViewModel", "Error loading video $videoId", e)
           }
        }
    }

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

        
        viewModelScope.launch {
            try {

                val response = com.github.libretube.test.api.MediaServiceRepository.instance.getDeArrowContent(videoId)
                if (response != null && (response.titles.isNotEmpty() || response.thumbnails.isNotEmpty())) {

                } else {

                }
                deArrowData.postValue(response)
            } catch (e: Exception) {
                // Error fetching DeArrow data
                deArrowData.postValue(null)
            }
        }
    }

    fun sortQueue(index: Int) {
        val streams = PlayingQueue.getStreams()
        val currentIndex = PlayingQueue.currentIndex()
        val newQueue = when (index) {
            0 -> streams.sortedBy { it.uploaded }
            1 -> streams.sortedBy { it.views }.reversed()
            2 -> streams.sortedBy { it.uploaderName }
            3 -> {
                // save all streams that need to be shuffled to a copy of the list
                val toShuffle = streams.filterIndexed { queueIndex, _ ->
                    queueIndex > currentIndex
                }
                // create a new list by replacing the old queue-end with the new, shuffled one
                streams
                    .filter { it !in toShuffle }
                    .plus(toShuffle.shuffled())
            }
            4 -> streams.reversed()
            else -> streams
        }
        PlayingQueue.setStreams(newQueue)
    }

    fun updateWatchPositions(index: Int) {
        val streams = PlayingQueue.getStreams()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            when (index) {
                0 -> {
                    streams.forEach {
                        val videoId = it.url.orEmpty().toID()
                        val duration = it.duration ?: 0
                        val watchPosition = com.github.libretube.test.db.obj.WatchPosition(videoId, duration * 1000)
                        com.github.libretube.test.db.DatabaseHolder.Database.watchPositionDao().insert(watchPosition)
                    }
                }
                1 -> {
                    streams.forEach {
                        com.github.libretube.test.db.DatabaseHolder.Database.watchPositionDao()
                            .deleteByVideoId(it.url.orEmpty().toID())
                    }
                }
                2 -> {
                    val currentStream = PlayingQueue.getCurrent()
                    val filteredStreams = com.github.libretube.test.db.DatabaseHelper
                        .filterUnwatched(streams)
                        .toMutableList()
                    if (currentStream != null &&
                        filteredStreams.none { it.url?.toID() == currentStream.url?.toID() }
                    ) {
                        filteredStreams.add(0, currentStream)
                    }
                    PlayingQueue.setStreams(filteredStreams)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release the player or controller if needed
        // Since we are using a Service, we might simply disconnect the controller
        playerController.value?.release()
        _playerController.value = null
    }

    fun onUserLeaveHint() {
        // implemented in MainActivity
    }
}
