package com.github.libretube.test.ui.fragments

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.math.MathUtils.clamp
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.libretube.test.R
import com.github.libretube.test.api.JsonHelper
import com.github.libretube.test.api.obj.ChapterSegment
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.constants.IntentData
import android.util.Log
import com.github.libretube.test.databinding.FragmentAudioPlayerBinding
import com.github.libretube.test.enums.PlayerCommand
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.navigateVideo
import com.github.libretube.test.extensions.normalize
import com.github.libretube.test.extensions.seekBy
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.extensions.togglePlayPauseState
import com.github.libretube.test.extensions.updateIfChanged
import com.github.libretube.test.api.obj.Streams
import com.github.libretube.test.util.StoryboardHelper
import com.github.libretube.test.util.StoryboardTransformation
import coil3.request.ImageRequest
import coil3.BitmapImage
import coil3.request.transformations
import com.github.libretube.test.helpers.AudioHelper
import com.github.libretube.test.helpers.BackgroundHelper
import com.github.libretube.test.helpers.ClipboardHelper
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NavBarHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PlayerHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.helpers.ThemeHelper
import com.github.libretube.test.obj.VideoResolution
import com.github.libretube.test.services.AbstractPlayerService
import com.github.libretube.test.services.OfflinePlayerService
import com.github.libretube.test.services.OnlinePlayerService
import com.github.libretube.test.ui.activities.MainActivity
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.extensions.getSystemInsets
import com.github.libretube.test.ui.extensions.setOnBackPressed
import com.github.libretube.test.ui.interfaces.AudioPlayerOptions
import com.github.libretube.test.ui.listeners.AudioPlayerThumbnailListener
import com.github.libretube.test.ui.models.ChaptersViewModel
import com.github.libretube.test.ui.models.CommonPlayerViewModel
import com.github.libretube.test.ui.sheets.BaseBottomSheet
import com.github.libretube.test.ui.sheets.ChaptersBottomSheet
import com.github.libretube.test.ui.sheets.PlaybackOptionsSheet
import com.github.libretube.test.ui.sheets.PlayingQueueSheet
import com.github.libretube.test.ui.sheets.SleepTimerSheet
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.test.util.DataSaverMode
import com.github.libretube.test.util.PlayingQueue
import com.github.libretube.test.db.DatabaseHelper
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.WatchPosition
import com.github.libretube.test.extensions.setActionListener
import com.github.libretube.test.ui.adapters.PlayingQueueAdapter
import com.github.libretube.test.ui.dialogs.AddToPlaylistDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs

@UnstableApi
class AudioPlayerFragment : Fragment(R.layout.fragment_audio_player), AudioPlayerOptions {
    private var _binding: FragmentAudioPlayerBinding? = null
    val binding get() = _binding!!

    private lateinit var audioHelper: AudioHelper
    private val activity get() = context as BaseActivity
    private val mainActivity get() = activity as? MainActivity
    private val mainActivityMotionLayout get() = mainActivity?.binding?.mainMotionLayout
    private val viewModel: CommonPlayerViewModel by activityViewModels()
    private val chaptersModel: ChaptersViewModel by activityViewModels()
    
    private lateinit var queueAdapter: PlayingQueueAdapter
    private var streams: Streams? = null

    // for the transition
    private var transitionStartId = 0
    private var transitionEndId = 0

    private var handler = Handler(Looper.getMainLooper())
    private var isPaused = !PlayerHelper.playAutomatically

    private var isOffline: Boolean = false
    private var playerController: MediaController? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        Log.e("LibreTube", "AudioPlayerFragment onAttach: hasMainActivity=${context is MainActivity}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("LibreTube", "AudioPlayerFragment onCreate: hash=${hashCode()}")

        audioHelper = AudioHelper(requireContext())

        isOffline = requireArguments().getBoolean(IntentData.offlinePlayer)

        BackgroundHelper.startMediaService(
            requireContext(),
            if (isOffline) OfflinePlayerService::class.java else OnlinePlayerService::class.java,
        ) {
            if (_binding == null) {
                it.sendCustomCommand(AbstractPlayerService.stopServiceCommand, Bundle.EMPTY)
                it.release()
                return@startMediaService
            }

            playerController = it
            handleServiceConnection()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        
        binding.headerContainer.isVisible = !isInPictureInPictureMode
        binding.controlsContainer.isVisible = !isInPictureInPictureMode
        binding.artworkContainer.isVisible = !isInPictureInPictureMode
        binding.audioPlayerContainer.isVisible = !isInPictureInPictureMode
        
        // Handle mini player controls visibility
        if (!isInPictureInPictureMode) {
            // Restore mini player visibility if valid
            binding.miniPlayerControls.isVisible = viewModel.isMiniPlayerVisible.value == true
        } else {
             binding.miniPlayerControls.isVisible = false
        }
        
        // If in PiP, ensure video is visible
        if (isInPictureInPictureMode) {
            binding.videoPlayerView.player = playerController
            binding.videoContainer.isVisible = true
            binding.videoContainer.alpha = 1.0f
            // Hide the thumbnail so it doesn't cover the video if constraints collapse uniquely
            binding.thumbnail.isVisible = false 
        } else {
            // Restore state based on inline video toggle
            if (isInlineVideoEnabled) {
                binding.videoPlayerView.player = playerController
                binding.videoContainer.isVisible = true
                binding.videoContainer.alpha = 1.0f
                binding.thumbnail.isVisible = false
            } else {
                binding.videoPlayerView.player = null
                binding.videoContainer.isVisible = false
                binding.thumbnail.isVisible = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAudioPlayerBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)


        // manually apply additional padding for edge-to-edge compatibility
        // manually apply additional padding for edge-to-edge compatibility
        activity.getSystemInsets()?.let { systemBars ->
            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                binding.headerContainer.paddingTop + systemBars.top,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )
            binding.controlsContainer.setPadding(
                binding.controlsContainer.paddingLeft,
                binding.controlsContainer.paddingTop,
                binding.controlsContainer.paddingRight,
                binding.controlsContainer.paddingBottom + systemBars.bottom
            )
        }

        initializeTransitionLayout()

        // binding.title.isSelected = true 
        // binding.uploader.isSelected = true

        binding.title.setOnLongClickListener {
            ClipboardHelper.save(requireContext(), text = binding.title.text.toString())
            true
        }

        binding.minimizePlayer.setOnClickListener {
            mainActivityMotionLayout?.transitionToStart()
            binding.playerMotionLayout.transitionToEnd()
        }

        binding.prev.setOnClickListener {
            playerController?.navigateVideo(PlayingQueue.getPrev() ?: return@setOnClickListener)
        }

        binding.next.setOnClickListener {
            playerController?.navigateVideo(PlayingQueue.getNext() ?: return@setOnClickListener)
        }

        binding.rewindBTN.setOnClickListener {
            playerController?.seekBy(-PlayerHelper.seekIncrement)
        }
        binding.forwardBTN.setOnClickListener {
            playerController?.seekBy(PlayerHelper.seekIncrement)
        }

        childFragmentManager.setFragmentResultListener(
            PlayingQueueSheet.PLAYING_QUEUE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, args ->
            playerController?.navigateVideo(
                args.getString(IntentData.videoId) ?: return@setFragmentResultListener
            )
        }
        binding.openQueue.setOnClickListener {
            viewModel.isMiniPlayerVisible.value = false // Hide Nav immediately
            binding.playerMotionLayout.setTransition(R.id.start, R.id.queue_expanded)
            binding.playerMotionLayout.transitionToEnd()
        }

        binding.queueDragHandle.setOnClickListener {
            binding.playerMotionLayout.transitionToStart()
        }

        binding.playbackOptions.setOnClickListener {
            onSpeedClicked()
        }

        binding.showMore.setOnClickListener {
            onShowMoreClicked()
        }

        binding.toggleInlineVideo.setOnClickListener {
            toggleInlineVideo()
        }

        // Add tooltips on long press
        listOf(
            binding.videoQuality to R.string.quality,
            binding.toggleInlineVideo to R.string.inline_video,
            binding.playbackOptions to R.string.speed,
            binding.openVideo to R.string.video,
            binding.openQueue to R.string.queue,
            binding.showMore to R.string.more
        ).forEach { (view, stringRes) ->
            view.setOnLongClickListener {
                Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show()
                true
            }
        }

        binding.sleepTimer.setOnClickListener {
            SleepTimerSheet().show(childFragmentManager)
        }

        binding.openVideo.setOnClickListener {
            val currentId = PlayingQueue.getCurrent()?.url?.toID()
            switchToVideoMode(currentId ?: return@setOnClickListener)
        }

        binding.videoQuality.setOnClickListener {
            onQualityClicked()
        }

        childFragmentManager.setFragmentResultListener(
            ChaptersBottomSheet.SEEK_TO_POSITION_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            playerController?.seekTo(bundle.getLong(IntentData.currentPosition))
        }

        binding.subtitle.setOnClickListener {
            onSubtitleClicked()
        }

        binding.currentChapter.setOnClickListener {
            showChaptersSheet()
        }

        binding.miniPlayerClose.setOnClickListener {
            killFragment(true)
        }

        val listener = AudioPlayerThumbnailListener(requireContext(), this)
        binding.thumbnail.setOnTouchListener(listener)
        binding.videoPlayerView.setOnTouchListener(listener)

        binding.playPause.setOnClickListener {
            playerController?.togglePlayPauseState()
        }

        binding.miniPlayerPause.setOnClickListener {
            playerController?.togglePlayPauseState()
        }

        binding.playerMotionLayout.addSwipeDownListener {
            val state = binding.playerMotionLayout.currentState
            val stateName = try { resources.getResourceEntryName(state) } catch (e: Exception) { state.toString() }
            Log.d("LibreTube", "Swipe down detected. CurrentState: $stateName ($state) Progress: ${binding.playerMotionLayout.progress}")
            
            if (state == R.id.queue_expanded) {
                // If Queue is open, Swipe DOWN always closes Queue (to Full Player)
                binding.playerMotionLayout.transitionToStart()
            } else if (binding.playerMotionLayout.progress > 0.95f && state != -1 && state != R.id.start) {
                // If Mini Player (High Progress) AND NOT transitioning AND NOT Full Player
                // Close Player (Mini -> Gone)
                Log.d("LibreTube", "Closing player (mini)")
                killFragment(true)
            }
        }

        // update the currently shown volume
        binding.volumeProgressBar.let { bar ->
            bar.progress = audioHelper.getVolumeWithScale(bar.max)
        }

        if (!PlayerHelper.playAutomatically) updatePlayPauseButton()

        updateChapterIndex()

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.playerMotionLayout.currentState == R.id.queue_expanded) {
                    binding.playerMotionLayout.transitionToStart()
                    return
                }
                
                binding.audioPlayerContainer.isClickable = false
                binding.playerMotionLayout.transitionToEnd()
                mainActivityMotionLayout?.transitionToEnd()
                mainActivity?.requestOrientationChange()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                binding.playerMotionLayout.progress = backEvent.progress
            }

            override fun handleOnBackCancelled() {
                binding.playerMotionLayout.transitionToStart()
            }
        }
        setOnBackPressed(onBackPressedCallback)

        viewModel.isMiniPlayerVisible.observe(viewLifecycleOwner) { isMiniPlayerVisible ->
            // re-add the callback on top of the back pressed dispatcher listeners stack,
            // so that it's the first one to become called while the full player is visible
            if (!isMiniPlayerVisible) {
                onBackPressedCallback.remove()
                setOnBackPressed(onBackPressedCallback)
            }

            // if the player is minimized, the fragment behind the player should handle the event
            onBackPressedCallback.isEnabled = isMiniPlayerVisible != true
        }

        initializeQueue()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initializeQueue() {
        binding.queueRecycler.layoutManager = LinearLayoutManager(context)
        queueAdapter = PlayingQueueAdapter { videoId ->
            playerController?.navigateVideo(videoId)
        }
        binding.queueRecycler.adapter = queueAdapter

        // scroll to the currently playing video in the queue
        val currentPlayingIndex = PlayingQueue.currentIndex()
        if (currentPlayingIndex != -1) binding.queueRecycler.scrollToPosition(currentPlayingIndex)

        binding.queueAddToPlaylist.setOnClickListener {
            AddToPlaylistDialog().show(childFragmentManager, null)
        }

        binding.queueRepeat.setOnClickListener {
            PlayingQueue.repeatMode = when (PlayingQueue.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            updateQueueRepeatButton()
        }
        updateQueueRepeatButton()

        binding.queueClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tooltip_clear_queue)
                .setPositiveButton(R.string.okay) { _, _ ->
                    val currentIndex = PlayingQueue.currentIndex()
                    PlayingQueue.setStreams(
                        PlayingQueue.getStreams()
                            .filterIndexed { index, _ -> index == currentIndex }
                    )
                    queueAdapter.notifyDataSetChanged()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.queueSort.setOnClickListener {
            showQueueSortDialog()
        }

        binding.queueWatchPositions.setOnClickListener {
            showQueueWatchPositionsOptions()
        }

        binding.queueAutoPlaySwitch.isChecked = PlayerHelper.autoPlayEnabled
        binding.queueAutoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            PlayerHelper.autoPlayEnabled = isChecked
        }

        binding.queueRecycler.setActionListener(
            allowSwipe = true,
            allowDrag = true,
            onDismissedListener = { position ->
                if (position == PlayingQueue.currentIndex()) {
                    queueAdapter.notifyItemChanged(position)
                    return@setActionListener
                }
                PlayingQueue.remove(position)
                queueAdapter.notifyItemRemoved(position)
                queueAdapter.notifyItemRangeChanged(position, queueAdapter.itemCount)
            },
            onDragListener = { from, to ->
                PlayingQueue.move(from, to)
                queueAdapter.notifyItemMoved(from, to)
            }
        )
    }

    private fun updateQueueRepeatButton() {
        binding.queueRepeat.alpha = if (PlayingQueue.repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f
        val drawableResource = if (PlayingQueue.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        binding.queueRepeat.setIconResource(drawableResource)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showQueueSortDialog() {
        val sortOptions = listOf(
            R.string.creation_date,
            R.string.most_views,
            R.string.uploader_name,
            R.string.shuffle,
            R.string.tooltip_reverse
        ).map { requireContext().getString(it) }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setItems(sortOptions) { _, index ->
                val newQueue = when (index) {
                    0 -> PlayingQueue.getStreams().sortedBy { it.uploaded }
                    1 -> PlayingQueue.getStreams().sortedBy { it.views }.reversed()
                    2 -> PlayingQueue.getStreams().sortedBy { it.uploaderName }
                    3 -> {
                        val streams = PlayingQueue.getStreams()
                        val currentIndex = PlayingQueue.currentIndex()
                        val toShuffle = streams.filterIndexed { queueIndex, _ -> queueIndex > currentIndex }
                        streams.filter { it !in toShuffle }.plus(toShuffle.shuffled())
                    }
                    4 -> PlayingQueue.getStreams().reversed()
                    else -> return@setItems
                }
                PlayingQueue.setStreams(newQueue)
                queueAdapter.notifyDataSetChanged()
            }
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showQueueWatchPositionsOptions() {
        val options = arrayOf(
            getString(R.string.mark_as_watched),
            getString(R.string.mark_as_unwatched),
            getString(R.string.remove_watched_videos)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.watch_positions)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            PlayingQueue.getStreams().forEach {
                                val videoId = it.url.orEmpty().toID()
                                val duration = it.duration ?: 0
                                DatabaseHolder.Database.watchPositionDao().insert(WatchPosition(videoId, duration * 1000))
                            }
                        }
                    }
                    1 -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            PlayingQueue.getStreams().forEach {
                                DatabaseHolder.Database.watchPositionDao().deleteByVideoId(it.url.orEmpty().toID())
                            }
                        }
                    }
                    2 -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val currentStream = PlayingQueue.getCurrent()
                            val streams = DatabaseHelper.filterUnwatched(PlayingQueue.getStreams()).toMutableList()
                            if (currentStream != null && streams.none { it.url?.toID() == currentStream.url?.toID() }) {
                                streams.add(0, currentStream)
                            }
                            PlayingQueue.setStreams(streams)
                            withContext(Dispatchers.Main) {
                                queueAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
            .show()
    }

    fun switchToVideoMode(videoId: String) {
        playerController?.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            bundleOf(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name to false)
        )

        killFragment(false)

        NavigationHelper.openVideoPlayerFragment(
            context = requireContext(),
            videoId = videoId,
            alreadyStarted = true,
        )
    }

    private fun killFragment(stopPlayer: Boolean) {
        viewModel.isMiniPlayerVisible.value = false

        if (stopPlayer) playerController?.sendCustomCommand(
            AbstractPlayerService.stopServiceCommand,
            Bundle.EMPTY
        )
        playerController?.release()
        playerController = null

        viewModel.isFullscreen.value = false
        binding.playerMotionLayout.transitionToEnd()
        activity.supportFragmentManager.commit {
            remove(this@AudioPlayerFragment)
        }
    }
    
    private var isInlineVideoEnabled = false
    
    private fun toggleInlineVideo() {
        Log.d("LibreTube", "toggleInlineVideo clicked, current state: $isInlineVideoEnabled")
        val player = playerController ?: run {
            Log.e("LibreTube", "toggleInlineVideo: playerController is NULL")
            return
        }
        
        isInlineVideoEnabled = !isInlineVideoEnabled
        
        val motionLayout = binding.playerMotionLayout
        val startConstraintSet = motionLayout.getConstraintSet(R.id.start)
        
        if (isInlineVideoEnabled) {
            Log.d(TAG(), "toggleInlineVideo: Enabling video. Hiding thumbnail, Showing videoView.")
            
            // Update View properties directly
            binding.thumbnail.isGone = true
            binding.videoContainer.isVisible = true
            binding.videoContainer.alpha = 1.0f
            binding.videoPlayerView.player = player
            binding.toggleInlineVideo.setIconResource(R.drawable.ic_image)
            
            // Update ConstraintSet to ensure MotionLayout respects it
            // thumbnail is NOT in MotionScene, so do NOT touch it in ConstraintSet (it breaks layout)
            startConstraintSet?.setVisibility(R.id.video_container, View.VISIBLE)
            startConstraintSet?.setAlpha(R.id.video_container, 1.0f)
            
            player.sendCustomCommand(
                AbstractPlayerService.runPlayerActionCommand,
                bundleOf(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name to false)
            )
        } else {
            Log.d(TAG(), "toggleInlineVideo: Disabling video. Showing thumbnail, Hiding videoView.")
            
            // Update View properties directly
            binding.videoPlayerView.player = null
            binding.videoContainer.isGone = true
            binding.videoContainer.alpha = 0.0f // Force hiding
            binding.thumbnail.isVisible = true
            binding.thumbnail.alpha = 1.0f
            binding.toggleInlineVideo.setIconResource(R.drawable.ic_movie)
            
            // Update ConstraintSet
            // thumbnail is NOT in MotionScene, so do NOT touch it in ConstraintSet
            startConstraintSet?.setVisibility(R.id.video_container, View.GONE)
            startConstraintSet?.setAlpha(R.id.video_container, 0.0f)
            
            player.sendCustomCommand(
                AbstractPlayerService.runPlayerActionCommand,
                bundleOf(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name to true)
            )
        }
        
        // Apply updates to the MotionLayout
        // Note: We use updateState because we are modifying the ConstraintSet in place
        // and want it to reflect immediately if we are in that state.
        if (startConstraintSet != null) {
            motionLayout.updateState(R.id.start, startConstraintSet)
            // If we are currently in start state, request layout to apply
            if (motionLayout.currentState == R.id.start) {
                 motionLayout.requestLayout()
            }
        }


        
        Log.d(TAG(), "toggleInlineVideo: END. thumbVis=${binding.thumbnail.visibility}, vidVis=${binding.videoContainer.visibility}")
    }

    fun playNextVideo(videoId: String) {
        playerController?.navigateVideo(videoId)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeTransitionLayout() {
        mainActivityMotionLayout?.progress = 0F

        binding.playerMotionLayout.addTransitionListener(object : TransitionAdapter() {
            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {
                if (NavBarHelper.hasTabs()) {
                    if (endId == R.id.queue_expanded || startId == R.id.queue_expanded) {
                        mainActivityMotionLayout?.progress = 0F
                    } else {
                        mainActivityMotionLayout?.progress = abs(progress)
                    }
                }
                transitionEndId = endId
                transitionStartId = startId
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (currentId == R.id.end) {
                    viewModel.isMiniPlayerVisible.value = true
                    if (NavBarHelper.hasTabs()) {
                         mainActivityMotionLayout?.progress = 1F
                    }
                } else if (currentId == R.id.start || currentId == R.id.queue_expanded) {
                    viewModel.isMiniPlayerVisible.value = false
                    
                    if (currentId == R.id.start) {
                        // Ensure we reset to the default transition (Start -> End)
                        // This handles coming back from Queue (Start -> Queue)
                        binding.playerMotionLayout.setTransition(R.id.start, R.id.end)
                    }
                    
                    if (NavBarHelper.hasTabs()) {
                         mainActivityMotionLayout?.progress = 0F
                    }
                }
            }
        })

        if (arguments?.getBoolean(IntentData.minimizeByDefault, false) != true) {
            binding.playerMotionLayout.progress = 1f
            binding.playerMotionLayout.transitionToStart()
        } else {
            binding.playerMotionLayout.progress = 0f
            binding.playerMotionLayout.transitionToEnd()
        }
    }

    /**
     * Load the information from a new stream into the UI
     */
    private fun updateStreamInfo(metadata: MediaMetadata) {
        val binding = _binding ?: return

        binding.title.text = metadata.title
        binding.miniPlayerTitle.text = metadata.title

        binding.uploader.text = metadata.artist
        binding.uploader.setOnClickListener {
            val uploaderId = metadata.composer?.toString() ?: return@setOnClickListener
            NavigationHelper.navigateChannel(requireContext(), uploaderId)
        }

        metadata.artworkUri?.let { updateThumbnailAsync(it) }

        initializeSeekBar()
    }

    private fun updateThumbnailAsync(thumbnailUri: Uri) {
        if (DataSaverMode.isEnabled(requireContext()) && !isOffline) {
            binding.progress.isVisible = false
            binding.thumbnail.setImageResource(R.drawable.ic_launcher_monochrome)
            val primaryColor = ThemeHelper.getThemeColor(
                requireContext(),
                androidx.appcompat.R.attr.colorPrimary
            )
            binding.thumbnail.setColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN)
            return
        }

        binding.progress.isVisible = true
        binding.thumbnail.isGone = true
        // reset color filter if data saver mode got toggled or conditions for it changed
        binding.thumbnail.setColorFilter(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC_IN)

        lifecycleScope.launch {
            val binding = _binding ?: return@launch
            
            // Load main thumbnail (High Quality)
            val bitmap = ImageHelper.getImage(requireContext(), thumbnailUri)
            binding.thumbnail.setImageBitmap(bitmap)
            binding.miniPlayerThumbnail.setImageBitmap(bitmap)
            binding.thumbnail.clearColorFilter()
            

            
            // Only show thumbnail if inline video is NOT enabled
            if (!isInlineVideoEnabled) {
                binding.thumbnail.isVisible = true
            } else {
                 Log.d(TAG(), "updateThumbnailAsync: Kept thumbnail hidden because inline video is enabled")
            }
            binding.progress.isGone = true
        }
    }

    private fun initializeSeekBar() {
        binding.timeBar.addSeekBarListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                // optional: pause playback?
            }

            override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                binding.currentPosition.text = DateUtils.formatElapsedTime(position / 1000)
            }

            override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                if (!canceled) {
                    playerController?.seekTo(position)
                }
            }
        })
        binding.timeBar.setPlayer(playerController ?: return)
        updateSeekBar()
    }

    /**
     * Update the position, duration and text views belonging to the seek bar
     */
    private fun updateSeekBar() {
        val binding = _binding ?: return
        val duration = playerController?.duration?.takeIf { it > 0 } ?: let {
            // if there's no duration available, clear everything
            binding.timeBar.setDuration(0)
            binding.timeBar.setPosition(0)
            binding.duration.text = ""
            binding.currentPosition.text = ""
            handler.postDelayed(this::updateSeekBar, 100)
            return
        }
        val currentPosition = playerController?.currentPosition ?: 0
        val bufferedPosition = playerController?.bufferedPosition ?: 0

        // set the text for the indicators
        binding.duration.text = DateUtils.formatElapsedTime(duration / 1000)
        binding.currentPosition.text = DateUtils.formatElapsedTime(
            currentPosition / 1000
        )

        // update the time bar current value and maximum value
        binding.timeBar.setDuration(duration)
        binding.timeBar.setPosition(currentPosition)
        binding.timeBar.setBufferedPosition(bufferedPosition)

        handler.postDelayed(this::updateSeekBar, 200)
    }

    private fun updatePlayPauseButton() {
        playerController?.let {
            val binding = _binding ?: return

            val iconRes = PlayerHelper.getPlayPauseActionIcon(it)
            binding.playPause.setIconResource(iconRes)
            binding.miniPlayerPause.setImageResource(iconRes)
        }
    }

    private fun handleServiceConnection() {
        playerController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                updatePlayPauseButton()
                isPaused = !isPlaying
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)

                // JSON-encode as work-around for https://github.com/androidx/media/issues/564
                val maybeStreams: Streams? = mediaMetadata.extras?.getString(IntentData.streams)?.let {
                    JsonHelper.json.decodeFromString(it)
                }
                maybeStreams?.let { 
                    streams = it 
                    val hasSubtitles = !it.subtitles.isNullOrEmpty()
                    _binding?.subtitle?.isVisible = true
                    _binding?.subtitleText?.isVisible = true
                    
                    val alpha = if (hasSubtitles) 1.0f else 0.3f
                    _binding?.subtitle?.alpha = alpha
                    _binding?.subtitleText?.alpha = alpha
                    
                    _binding?.subtitle?.isEnabled = hasSubtitles
                }

                updateStreamInfo(mediaMetadata)
                val chapters: List<ChapterSegment>? =
                    mediaMetadata.extras?.getString(IntentData.chapters)?.let {
                        JsonHelper.json.decodeFromString(it)
                    }
                // _binding?.openChapters?.isVisible = !chapters.isNullOrEmpty() // Button removed
                // _binding?.chaptersText?.isVisible = !chapters.isNullOrEmpty()
                _binding?.timeBar?.setChapters(chapters.orEmpty())
                
                // Show chapter info layout if chapters exist
                _binding?.currentChapter?.isVisible = !chapters.isNullOrEmpty()

                val segments: List<com.github.libretube.test.api.obj.Segment>? =
                    mediaMetadata.extras?.getString(IntentData.segments)?.let {
                        JsonHelper.json.decodeFromString(it)
                    }
                _binding?.timeBar?.setSegments(segments.orEmpty())
                
                // Refresh queue adapter on media change
                queueAdapter.notifyDataSetChanged()
                val newIndex = PlayingQueue.currentIndex()
                if (newIndex != -1) _binding?.queueRecycler?.scrollToPosition(newIndex)
            }
        })
        playerController?.mediaMetadata?.let { updateStreamInfo(it) }

        initializeSeekBar()

        if (isOffline) {
            binding.openVideo.isGone = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSingleTap() {
        // do nothing
    }

    override fun onLongTap() {
        val current = PlayingQueue.getCurrent() ?: return
        VideoOptionsBottomSheet()
            .apply {
                arguments = bundleOf(IntentData.streamItem to current)
            }
            .show(childFragmentManager)
    }

    override fun onSwipe(distanceY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) return

        binding.volumeControls.isVisible = true
        updateVolume(distanceY)
    }

    override fun onSwipeEnd() {
        if (!PlayerHelper.swipeGestureEnabled) return

        binding.volumeControls.isGone = true
    }

    private fun updateVolume(distance: Float) {
        val bar = binding.volumeProgressBar
        binding.volumeControls.apply {
            if (isGone) {
                isVisible = true
                // Volume could be changed using other mediums, sync progress
                // bar with new value.
                bar.progress = audioHelper.getVolumeWithScale(bar.max)
            }
        }

        if (bar.progress == 0) {
            binding.volumeImageView.setImageResource(
                when {
                    distance > 0 -> R.drawable.ic_volume_up
                    else -> R.drawable.ic_volume_off
                }
            )
        }
        bar.incrementProgressBy(distance.toInt() / 3)
        audioHelper.setVolumeWithScale(bar.progress, bar.max)

        binding.volumeTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
    }

    private fun updateChapterIndex() {
        if (_binding == null) return
        handler.postDelayed(this::updateChapterIndex, 100)

        val currentIndex =
            PlayerHelper.getCurrentChapterIndex(
                playerController?.currentPosition ?: return,
                chaptersModel.chapters
            )
        
        currentIndex?.let { index ->
            chaptersModel.currentChapterIndex.updateIfChanged(index)
            val chapter = chaptersModel.chapters.getOrNull(index)
            _binding?.currentChapter?.text = chapter?.title
        } ?: run {
             _binding?.currentChapter?.text = ""
        }
    }

    private fun onShowMoreClicked() {
        val optionsList = mutableListOf(
            R.string.sleep_timer,
            R.string.speed,
            R.string.playback_options // For Pitch and advanced settings
        )

        BaseBottomSheet()
            .setSimpleItems(optionsList.map { getString(it) }) { which ->
                when (optionsList[which]) {
                    R.string.sleep_timer -> SleepTimerSheet().show(childFragmentManager)
                    R.string.speed -> onSpeedClicked()
                    R.string.playback_options -> {
                        playerController?.let {
                            PlaybackOptionsSheet(it).show(childFragmentManager)
                        }
                    }
                }
            }
            .show(childFragmentManager)
    }

    private fun onSpeedClicked() {
        val player = playerController ?: return
        val currentSpeed = player.playbackParameters.speed
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        
        BaseBottomSheet()
            .setSimpleItems(
                speeds.map { "${it}x" },
                preselectedItem = "${currentSpeed}x"
            ) { which ->
                val newSpeed = speeds[which]
                player.playbackParameters = PlaybackParameters(newSpeed, player.playbackParameters.pitch)
                PreferenceHelper.putString(PreferenceKeys.PLAYBACK_SPEED, newSpeed.toString())
            }
            .show(childFragmentManager)
    }

    private fun onQualityClicked() {
        Log.d("LibreTube", "onQualityClicked entered")
        val player = playerController ?: run {
            Log.e("LibreTube", "onQualityClicked: playerController is NULL")
            return
        }
        val resolutions = getAvailableResolutions(player)
        Log.d("LibreTube", "onQualityClicked: available resolutions count = ${resolutions.size}")
        val currentFormat = getCurrentVideoFormat(player)
        val currentResolution = currentFormat?.height ?: Int.MAX_VALUE

        BaseBottomSheet()
            .setSimpleItems(
                resolutions.map { it.name },
                preselectedItem = resolutions.firstOrNull {
                    it.resolution == currentResolution
                }?.name ?: getString(R.string.auto_quality)
            ) { which ->
                val newResolution = resolutions[which].resolution
                player.sendCustomCommand(
                    AbstractPlayerService.runPlayerActionCommand, bundleOf(
                        PlayerCommand.SET_RESOLUTION.name to newResolution
                    )
                )
            }
            .show(childFragmentManager)
    }

    private fun getCurrentVideoFormat(player: Player): Format? {
        for (trackGroup in player.currentTracks.groups) {
            if (trackGroup.type != C.TRACK_TYPE_VIDEO) continue

            for (i in 0 until trackGroup.length) {
                if (trackGroup.isTrackSelected(i)) return trackGroup.getTrackFormat(i)
            }
        }
        return null
    }

    private fun getAvailableResolutions(player: Player): List<VideoResolution> {
        val resolutions = player.currentTracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).map {
                    group.getTrackFormat(it).height
                }
            }
            .filter { it > 0 }
            .map { VideoResolution("${it}p", it) }
            .toSortedSet(compareByDescending { it.resolution })

        resolutions.add(VideoResolution(getString(R.string.auto_quality), Int.MAX_VALUE))
        return resolutions.toList()
    }

    private fun onSubtitleClicked() {
        val currentStreams = streams ?: return
        if (currentStreams.subtitles.isEmpty()) {
            Toast.makeText(context, R.string.no_subtitles_found, Toast.LENGTH_SHORT).show()
            return
        }

        val subtitles = currentStreams.subtitles
        // TODO: How to get currently selected subtitle?
        // PlayerFragment uses viewModel.currentSubtitle, but we don't have that viewmodel shared?
        // Or we can query the playerController track selection?
        // For now, simple list.
        
        val options = mutableListOf<String>()
        options.add(getString(R.string.off))
        options.addAll(subtitles.map { it.name ?: it.code ?: "?" })

        BaseBottomSheet()
            .setSimpleItems(options) { which ->
                val selectedSubtitle = if (which == 0) null else subtitles[which - 1]
                updateCurrentSubtitle(selectedSubtitle)
            }
            .show(childFragmentManager)
    }

    private fun updateCurrentSubtitle(subtitle: com.github.libretube.test.api.obj.Subtitle?) {
        playerController?.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand, bundleOf(
                PlayerCommand.SET_SUBTITLE.name to subtitle
            )
        )
    }

    private fun showChaptersSheet() {
        // JSON-encode as work-around for https://github.com/androidx/media/issues/564
        chaptersModel.chaptersLiveData.value =
            playerController?.mediaMetadata?.extras?.getString(IntentData.chapters)?.let {
                JsonHelper.json.decodeFromString(it)
            }

        ChaptersBottomSheet()
            .apply {
                arguments = bundleOf(
                    IntentData.duration to playerController?.duration?.div(1000)
                )
            }
            .show(childFragmentManager)
    }

    override fun onDestroy() {
        Log.e("LibreTube", "AudioPlayerFragment onDestroy: hash=${hashCode()}")
        super.onDestroy()
    }
}

