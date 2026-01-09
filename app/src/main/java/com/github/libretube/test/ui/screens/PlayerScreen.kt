package com.github.libretube.test.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.libretube.test.ui.models.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

import androidx.compose.ui.viewinterop.AndroidView
import com.github.libretube.test.ui.components.PlayerControls
import com.github.libretube.test.ui.sheets.QueueSheet
import com.github.libretube.test.ui.sheets.ChaptersSheet
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.api.obj.ChapterSegment

enum class PlayerState {
    Collapsed,
    Expanded
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels.toFloat()
    
    // AnchoredDraggable State
    val anchors = remember(configuration) {
        DraggableAnchors {
            PlayerState.Collapsed at (screenHeightPx - with(density) { 60.dp.toPx() }) // Mini player height
            PlayerState.Expanded at 0f
        }
    }
    
    val draggableState: AnchoredDraggableState<PlayerState> = remember {
        AnchoredDraggableState(
            initialValue = PlayerState.Collapsed,
            anchors = anchors,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = tween(),
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
        movableContentOf { modifier: Modifier ->
            VideoSurface(modifier = modifier, viewModel = playerViewModel)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val queue by playerViewModel.queue.collectAsState()
        val chapters by playerViewModel.chapters.collectAsState()
        
        var showQueue by remember { mutableStateOf(false) }
        var showChapters by remember { mutableStateOf(false) }

        DraggablePlayerPanel(
            state = draggableState,
            onClose = onClose,
            viewModel = playerViewModel,
            videoSurface = movableVideoSurface,
            onQueueClick = { showQueue = true },
            onChaptersClick = { showChapters = true }
        )

        if (showQueue) {
            QueueSheet(
                queue = queue,
                onItemClick = { item -> 
                    showQueue = false
                    playerViewModel.onQueueItemClicked(item)
                },
                onDismissRequest = { showQueue = false }
            )
        }

        if (showChapters) {
            ChaptersSheet(
                chapters = chapters,
                onChapterClick = { chapter ->
                    showChapters = false
                    playerViewModel.seekTo(chapter.start * 1000)
                },
                onDismissRequest = { showChapters = false }
            )
        }
    }
}

@Composable
fun FullPlayerContent(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    alpha: Float,
    onQueueClick: () -> Unit,
    onChaptersClick: () -> Unit
) {
    if (alpha > 0f) {
        Column(modifier = modifier.alpha(alpha).padding(top = 250.dp)) { // Padding for video area
             PlayerControls(
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                onQueueClick = onQueueClick,
                onChaptersClick = onChaptersClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Full Player Details", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel
) {
    val playerController by viewModel.playerController.collectAsState()

    AndroidView(
        factory = { context ->
            androidx.media3.ui.PlayerView(context).apply {
                useController = false // We use our own controls
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (playerController != null && view.player != playerController) {
                android.util.Log.d("PlayerScreen", "Attaching player to view: $playerController")
                view.player = playerController
            }
        },
        onRelease = { view ->
            view.player = null // Only detach, don't release singleton player
        },
        modifier = modifier.background(Color.Black)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggablePlayerPanel(
    state: AnchoredDraggableState<PlayerState>,
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    videoSurface: @Composable (Modifier) -> Unit,
    onQueueClick: () -> Unit,
    onChaptersClick: () -> Unit
) {
    val offset = state.requireOffset()
    val maxOffset = state.anchors.positionOf(PlayerState.Collapsed)
    val progress = (offset / maxOffset).coerceIn(0f, 1f)
    
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels.dp
    val miniWidth = 120.dp
    val miniHeight = 67.5.dp
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
        val scaleX = androidx.compose.ui.util.lerp(1f, miniWidth.value / fullWidth.value, progress)
        val scaleY = androidx.compose.ui.util.lerp(1f, miniHeight.value / fullHeight.value, progress)
        
        // Video container with graphicsLayer for high-performance scaling
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart)
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
                .zIndex(1f)
        ) {
            // Apply inverse scale to video surface to prevent aspect-ratio distortion (squashing)
            videoSurface(
                Modifier
                    .size(fullWidth, fullHeight)
                    .graphicsLayer {
                        this.scaleX = 1f / scaleX
                        this.scaleY = 1f / scaleY
                        this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            )

            // Gesture Overlay: Captured in Mini Player mode to prevent SurfaceView touch swallowing
            if (progress > 0.8f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Transparent)
                        .clickable(enabled = false) {} // Just to swallow/allow drag
                )
            }
        }

        MiniPlayerContent(
            modifier = Modifier
                .alpha(progress)
                .fillMaxWidth()
                .height(miniHeight)
                .padding(start = miniWidth),
            onClose = onClose
        )

        FullPlayerContent(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize(),
            alpha = (1f - progress * 2f).coerceIn(0f, 1f), // Fade out faster
            onQueueClick = onQueueClick,
            onChaptersClick = onChaptersClick
        )
    }
}

@Composable
fun MiniPlayerContent(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Mini Player", modifier = Modifier.weight(1f)) 
        
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

fun Modifier.alpha(alpha: Float) = this.graphicsLayer(alpha = alpha)
