package com.github.libretube.test.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.libretube.test.ui.models.PlayerViewModel
import com.github.libretube.test.ui.screens.PlayerState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggablePlayerPanel(
    state: AnchoredDraggableState<PlayerState>,
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    videoSurface: @Composable (Modifier, Boolean) -> Unit,
    onChaptersClick: () -> Unit,
    onVideoOptionsClick: () -> Unit,
    onCommentsClick: () -> Unit
) {
    val offset = state.requireOffset()
    val maxOffset = state.anchors.positionOf(PlayerState.Collapsed)
    val progress = (offset / maxOffset).coerceIn(0f, 1f)
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val miniWidth = 120.dp
    val miniHeight = 80.dp
    val fullWidth = screenWidth
    val fullHeight = screenWidth * 9f / 16f

    Box(
        modifier = Modifier
            .offset { IntOffset(x = 0, y = offset.roundToInt()) }
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Vertical
            )
    ) {
        val currentStream by viewModel.currentStream.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val playbackPosition by viewModel.playbackPosition.collectAsState()
        val duration by viewModel.duration.collectAsState()
        val playbackProgress = if (duration > 0) (playbackPosition.toFloat() / duration) else 0f

        val scaleX = androidx.compose.ui.util.lerp(1f, miniWidth.value / fullWidth.value, progress)
        val scaleY = androidx.compose.ui.util.lerp(1f, miniHeight.value / fullHeight.value, progress)
        
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val currentTopPadding = statusBarPadding * (1f - progress)
        
        val currentWidth = androidx.compose.ui.util.lerp(fullWidth.value, miniWidth.value, progress).dp
        val currentHeight = androidx.compose.ui.util.lerp(fullHeight.value, miniHeight.value, progress).dp
        
        val showControls by viewModel.showControls.collectAsState()
        val isAudioOnly by viewModel.isAudioOnlyMode.collectAsState()

        // Video container with layout-based resizing for SurfaceView compatibility
        // When collapsed (progress=1), this matches the mini player thumb area
        Box(
            modifier = Modifier
                .padding(top = currentTopPadding)
                .size(currentWidth, currentHeight)
                .zIndex(2f) 
        ) {
            // Show thumbnail in audio-only mode, otherwise show video surface
            if (isAudioOnly) {
                // Audio-only mode: Show thumbnail
                coil3.compose.AsyncImage(
                    model = currentStream?.thumbnail,
                    contentDescription = "Audio thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                // Scrim overlay for better control visibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
                )
                
                // Add Gesture Overlay to Audio Mode as well
                if (progress < 0.2f) { // Only when expanded
                    PlayerGestureOverlay(
                        onSeek = { seekAmount ->
                            val currentPosition = viewModel.currentPosition.value
                            val newPosition = (currentPosition + seekAmount).coerceAtLeast(0)
                            viewModel.seekTo(newPosition)
                        },
                        onTap = { viewModel.toggleControls() }
                    )
                }
            } else {
                // Normal video mode
                videoSurface(
                    Modifier.fillMaxSize(),
                    progress > 0.8f // Disable gestures if mostly collapsed
                )
            }

            // Gesture Overlay: Captured in Mini Player mode to prevent SurfaceView touch swallowing
            if (progress > 0.8f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Transparent)
                        .clickable(enabled = false) {} // Just to swallow/allow drag
                )
            }

            // Full Player Controls Overlay (Placed ABOVE the video surface and CONSTRAINED to its size)
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls && progress < 0.2f,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(alpha = (1f - progress * 5f).coerceIn(0f, 1f))
            ) {
                PlayerControls(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onChaptersClick = onChaptersClick,
                    onVideoOptionsClick = onVideoOptionsClick
                )
            }
        }

        // MiniPlayer UI Overlay
        val scope = rememberCoroutineScope()
        MiniPlayer(
            modifier = Modifier
                .zIndex(3f) // ABOVE the video surface
                .graphicsLayer(alpha = progress)
                .fillMaxWidth()
                .height(miniHeight)
                .padding(start = miniWidth)
                .background(MaterialTheme.colorScheme.surface), // Background only for metadata area
            title = currentStream?.title ?: "",
            channelName = currentStream?.uploaderName ?: "",
            thumbnailUrl = currentStream?.thumbnail,
            isPlaying = isPlaying,
            progress = playbackProgress,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onClick = {
                scope.launch {
                    state.animateTo(PlayerState.Expanded)
                }
            },
            onClose = onClose
        )

        // Full Player Content (Fade out when collapsing)
        FullPlayer(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f), // Base layer for scrollable content
            alpha = (1f - progress * 2f).coerceIn(0f, 1f), // Fade out faster
            onChaptersClick = onChaptersClick,
            onVideoOptionsClick = onVideoOptionsClick,
            onCommentsClick = onCommentsClick
        )
    }
}
