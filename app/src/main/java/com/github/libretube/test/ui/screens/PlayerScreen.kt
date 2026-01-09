package com.github.libretube.test.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
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
import androidx.compose.ui.zIndex
import com.github.libretube.test.ui.models.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

import androidx.compose.ui.viewinterop.AndroidView
import com.github.libretube.test.ui.components.PlayerControls

enum class PlayerState {
    Collapsed,
    Expanded
}

@OptIn(ExperimentalFoundationApi::class)
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
            // draggableState.snapTo(PlayerState.Collapsed) // TODO: Fix unresolved reference
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Touch pass-through: If collapsed, the background is transparent and should not consume clicks
            // The Box itself doesn't have a background, so clicks pass through unless hit by children
    ) {
        DraggablePlayerPanel(
            state = draggableState,
            onClose = onClose,
            viewModel = playerViewModel
        )
    }
}

@Composable
fun FullPlayerContent(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    alpha: Float
) {
    if (alpha > 0f) {
        Column(modifier = modifier.alpha(alpha).padding(top = 250.dp)) { // Padding for video area
             PlayerControls(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
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
    viewModel: PlayerViewModel
) {
    val offset = state.requireOffset()
    // Progress: 0.0 (Expanded) -> 1.0 (Collapsed)
    // Note: state.anchors.positionOf(PlayerState.Collapsed) is the MAX offset (bottom)
    val maxOffset = state.anchors.positionOf(PlayerState.Collapsed)
    val progress = (offset / maxOffset).coerceIn(0f, 1f)
    
    // Performance: Use layout offset instead of generic modifiers for the container
    Box(
        modifier = Modifier
            .offset { IntOffset(x = 0, y = offset.roundToInt()) }
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // Background for the player itself
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Vertical
            )
    ) {
        val density = LocalDensity.current
        val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels.dp
        
        // Video Morphing Calculations
        // Collapsed: Width = 120dp, Height = 68dp (16:9), X = 0, Y = 0 (relative to container)
        // Expanded: Width = ScreenWidth, Height = ScreenWidth * 9/16, X = 0, Y = 0
        
        val miniWidth = 120.dp
        val miniHeight = 67.5.dp // 16:9 of 120
        val fullWidth = screenWidth
        val fullHeight = screenWidth * 9f / 16f
        
        // Interpolate size
        val currentWidth by remember(progress) { derivedStateOf { androidx.compose.ui.unit.lerp(fullWidth, miniWidth, progress) } }
        val currentHeight by remember(progress) { derivedStateOf { androidx.compose.ui.unit.lerp(fullHeight, miniHeight, progress) } }
        
        VideoSurface(
            modifier = Modifier
                .size(currentWidth, currentHeight)
                .zIndex(1f), // Video always on top
            viewModel = viewModel
        )

        // Mini Player Content (Title, Play/Pause) - Fades in when collapsed
        MiniPlayerContent(
            modifier = Modifier
                .alpha(progress) // Visible when collapsed (1.0)
                .fillMaxWidth()
                .height(68.dp)
                .padding(start = miniWidth), // Offset content to right of video
            onClose = onClose
        )

        // Full Player Content (Controls, Details) - Fades in when expanded
        FullPlayerContent(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize(),
            alpha = 1f - progress // Visible when expanded (0.0)
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
        // We can access ViewModel here if we want real title (passed down or captured)
        // For now, placeholder or needs refactor to pass title
        // Let's use simple text for fix.
        Text("Mini Player", modifier = Modifier.weight(1f)) 
        
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

// Helper for alpha modifier
fun Modifier.alpha(alpha: Float) = this.graphicsLayer(alpha = alpha)
