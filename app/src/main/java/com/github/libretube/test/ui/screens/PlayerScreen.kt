package com.github.libretube.test.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.obj.VideoStats
import com.github.libretube.test.ui.models.PlayerViewModel
import kotlinx.coroutines.launch

import com.github.libretube.test.ui.components.VideoSurface
import com.github.libretube.test.ui.components.DraggablePlayerPanel
import com.github.libretube.test.ui.sheets.QueueSheet
import com.github.libretube.test.ui.sheets.ChaptersSheet
import com.github.libretube.test.ui.sheets.PlayerSettingsSheet
import com.github.libretube.test.ui.sheets.QualitySelectionSheet
import com.github.libretube.test.ui.sheets.SubtitleSelectionSheet
import com.github.libretube.test.ui.sheets.AudioTrackSelectionSheet
import com.github.libretube.test.ui.sheets.VideoOptionsSheet
import com.github.libretube.test.ui.sheets.DownloadBottomSheet
import com.github.libretube.test.ui.sheets.ShareBottomSheet
import com.github.libretube.test.enums.ShareObjectType
import android.content.Intent
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

enum class PlayerState {
    Collapsed,
    Expanded
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    bottomPadding: Dp = 0.dp
) {
    com.github.libretube.test.ui.theme.LibreTubeTheme {
        PlayerScreenContent(playerViewModel, onClose, bottomPadding)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreenContent(
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    bottomPadding: Dp
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()
    
    // AnchoredDraggable State
    val anchors = remember(configuration, screenHeightPx, bottomPadding) {
        DraggableAnchors {
            // Offset Collapsed anchor by (80dp miniplayer + provided bottom padding)
            val miniPlayerHeight = with(density) { 80.dp.toPx() }
            val paddingPx = with(density) { bottomPadding.toPx() }
            PlayerState.Collapsed at (screenHeightPx - miniPlayerHeight - paddingPx) 
            PlayerState.Expanded at 0f
        }
    }
    
    val draggableState: AnchoredDraggableState<PlayerState> = remember {
        AnchoredDraggableState(
            initialValue = PlayerState.Collapsed,
            anchors = anchors,
            positionalThreshold = { distance: Float -> distance * 0.7f }, // Higher threshold to avoid accidental swipes
            velocityThreshold = { with(density) { 200.dp.toPx() } }, // Higher velocity threshold
            snapAnimationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            ),
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { true }
        )
    }

    val scope = rememberCoroutineScope()
    // Back Handling
    BackHandler(enabled = draggableState.currentValue == PlayerState.Expanded) {
        scope.launch {
            draggableState.animateTo(PlayerState.Collapsed)
        }
    }

    // Movable Player Surface to prevent re-inflation
    val movableVideoSurface = remember(playerViewModel) {
        movableContentOf { modifier: Modifier, gesturesEnabled: Boolean ->
            VideoSurface(modifier = modifier, viewModel = playerViewModel, gesturesEnabled = gesturesEnabled)
        }
    }

    // Observe Minimize/Maximize Commands
    LaunchedEffect(playerViewModel) {
        playerViewModel.expandPlayerTrigger.collect {
            draggableState.animateTo(PlayerState.Expanded)
        }
    }
    LaunchedEffect(playerViewModel) {
        playerViewModel.collapsePlayerTrigger.collect {
            draggableState.animateTo(PlayerState.Collapsed)
        }
    }

    // System Bars Theme Synchronization
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        val isExpanded = draggableState.currentValue == PlayerState.Expanded
        val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
        
        SideEffect {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            // isAppearanceLightStatusBars = true means DARK icons on LIGHT background
            // we want DARK icons in light mode when NOT expanded
            insetsController.isAppearanceLightStatusBars = !isSystemDark && !isExpanded
            insetsController.isAppearanceLightNavigationBars = !isSystemDark && !isExpanded
        }
        
        // Update PlayerViewModel expansion state for MainActivity to hide/show BottomNav
        LaunchedEffect(isExpanded) {
            playerViewModel.setExpanded(isExpanded)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val queue by playerViewModel.queue.collectAsState()
        val chapters by playerViewModel.chapters.collectAsState()
        val currentStream by playerViewModel.currentStream.collectAsState()
        
        var showQueue by remember { mutableStateOf(false) }
        var showChapters by remember { mutableStateOf(false) }
        var showQuality by remember { mutableStateOf(false) }
        var showCaptions by remember { mutableStateOf(false) }
        var showSleepTimer by remember { mutableStateOf(false) }
        var showStats by remember { mutableStateOf(false) }
        var showAudioTracks by remember { mutableStateOf(false) }
        var showVideoOptions by remember { mutableStateOf(false) }
        var videoOptionsInitialScreen by remember { mutableStateOf("MAIN") }
        var showDownloadSheet by remember { mutableStateOf(false) }
        var showShareSheet by remember { mutableStateOf(false) }

        // Action Command Handling
        val context = LocalContext.current
        LaunchedEffect(playerViewModel) {
            playerViewModel.playerCommandTrigger.collect { command ->
                when (command) {
                    is com.github.libretube.test.ui.models.PlayerCommandEvent.Share -> {
                        showShareSheet = true
                    }
                    is com.github.libretube.test.ui.models.PlayerCommandEvent.Download -> {
                        showDownloadSheet = true
                    }
                    is com.github.libretube.test.ui.models.PlayerCommandEvent.SaveToPlaylist -> {
                        showVideoOptions = true
                        videoOptionsInitialScreen = "ADD_TO_PLAYLIST"
                    }
                    is com.github.libretube.test.ui.models.PlayerCommandEvent.Bookmark -> {
                        // Handled in MetadataSection via ViewModel toggle for now
                        // If we want a separate BookmarkSheet, add it here
                    }
                    else -> Unit
                }
            }
        }

        val isInPip by playerViewModel.isInPip.collectAsState()
        val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
        val playbackPitch by playerViewModel.playbackPitch.collectAsState()

        if (!isInPip) {
            DraggablePlayerPanel(
                state = draggableState,
                onClose = onClose,
                viewModel = playerViewModel,
                videoSurface = movableVideoSurface,
                onChaptersClick = { showChapters = true },
                onVideoOptionsClick = { 
                    videoOptionsInitialScreen = "MAIN"
                    showVideoOptions = true 
                },
                onCommentsClick = { playerViewModel.openCommentsSheet() }
            )
        } else {
            // In PIP, only show the video surface filling the panel
            // We use a simplified version of the draggable panel or just the surface
            Box(Modifier.fillMaxSize()) {
                movableVideoSurface(Modifier.fillMaxSize(), true)
            }
        }

        val showCommentsSheet by playerViewModel.showCommentsSheet.collectAsState()
        if (showCommentsSheet && !isInPip) {
            val commentsViewModel: com.github.libretube.test.ui.models.CommentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val currentVideoId = currentStream?.url?.toID()
            
            // Re-initialize comments VM if video changed
            LaunchedEffect(currentVideoId) {
                currentVideoId?.let { 
                    commentsViewModel.videoIdLiveData.value = it 
                }
            }

            com.github.libretube.test.ui.sheets.CommentsSheetCompose(
                viewModel = commentsViewModel,
                onDismiss = { playerViewModel.closeCommentsSheet() }
            )
        }

        if (showQueue && !isInPip) {
            QueueSheet(
                queue = queue,
                onItemClick = { item -> 
                    showQueue = false
                    playerViewModel.onQueueItemClicked(item)
                },
                onDismissRequest = { showQueue = false }
            )
        }

        if (showChapters && !isInPip) {
            ChaptersSheet(
                chapters = chapters,
                onChapterClick = { chapter ->
                    showChapters = false
                    playerViewModel.seekTo(chapter.start * 1000)
                },
                onDismissRequest = { showChapters = false }
            )
        }


        if (showVideoOptions && !isInPip) {
            com.github.libretube.test.ui.sheets.ConsolidatedOptionsSheet(
                viewModel = playerViewModel,
                onDismissRequest = { 
                    showVideoOptions = false
                },
                onQualityClick = {
                    showQuality = true
                },
                onCaptionsClick = {
                    showCaptions = true
                },
                onAudioTrackClick = {
                    showAudioTracks = true
                },
                onSleepTimerClick = {
                    showSleepTimer = true
                },
                onStatsClick = {
                    showStats = true
                }
            )
        }

        if (showSleepTimer && !isInPip) {
             com.github.libretube.test.ui.sheets.SleepTimerSheetCompose(
                 onDismiss = { showSleepTimer = false }
             )
        }

        if (showStats && !isInPip) {
            val currentStream by playerViewModel.currentStream.collectAsState()
            val stats = remember(currentStream) {
                VideoStats(
                    videoId = currentStream?.url?.toID() ?: "Unknown",
                    videoInfo = "ExoPlayer (Hardware)",
                    videoQuality = "Auto (Adaptive)",
                    audioInfo = "AAC / Opus"
                )
            }
            com.github.libretube.test.ui.sheets.StatsSheetCompose(
                stats = stats,
                onDismiss = { showStats = false }
            )
        }

        if (showQuality && !isInPip) {
            playerViewModel.playerController.value?.let { controller ->
                QualitySelectionSheet(
                    player = controller,
                    onDismiss = { showQuality = false }
                )
            }
        }

        if (showCaptions && !isInPip) {
            playerViewModel.playerController.value?.let { controller ->
                SubtitleSelectionSheet(
                    player = controller,
                    onDismiss = { showCaptions = false }
                )
            }
        }

        if (showAudioTracks && !isInPip) {
            playerViewModel.playerController.value?.let { controller ->
                AudioTrackSelectionSheet(
                    player = controller,
                    onDismiss = { showAudioTracks = false }
                )
            }
        }



        if (showDownloadSheet && !isInPip) {
            val videoId = currentStream?.url?.toID()
            if (videoId != null) {
                DownloadBottomSheet(
                    videoId = videoId,
                    onDismissRequest = { showDownloadSheet = false }
                )
            }
        }

        if (showShareSheet && !isInPip) {
            val videoId = currentStream?.url?.toID()
            if (videoId != null) {
                ShareBottomSheet(
                    id = videoId,
                    title = currentStream?.title ?: "",
                    shareObjectType = ShareObjectType.VIDEO,
                    initialTimestamp = "0",
                    onDismissRequest = { showShareSheet = false }
                )
            }
        }
    }
}
