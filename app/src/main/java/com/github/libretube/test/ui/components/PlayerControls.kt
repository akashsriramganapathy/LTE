package com.github.libretube.test.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.libretube.test.ui.models.PlayerViewModel
import com.github.libretube.test.ui.components.formatDuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.github.libretube.test.R

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onChaptersClick: () -> Unit,
    onVideoOptionsClick: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val areLocked by viewModel.areControlsLocked.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Shadow Scrim Top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // Shadow Scrim Bottom (Covers progress area)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        // Top Row: [X] [Lock] ... [Pip] [Chapters] [More]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Collapse handled by parent */ }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                IconButton(onClick = { viewModel.toggleControlsLock() }) {
                    Icon(
                        if (areLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Controls",
                        tint = Color.White
                    )
                }
            }

            // Right Group
            if (!areLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setIsInPip(true) }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onChaptersClick) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "Chapters", tint = Color.White)
                    }
                    IconButton(onClick = onVideoOptionsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }
        }

        // Center Controls: [Prev] [Big Play/Pause] [Next]
        if (!areLocked) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(42.dp))
                }
                
                Spacer(modifier = Modifier.width(32.dp))

                Box(contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(64.dp))
                    }
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
        }

        // Bottom Area: [Time] ... [Fullscreen] 
        // Progress bar at the very bottom
        if (!areLocked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time: 00:02 • 43:17
                    Text(
                        text = "${formatDuration(currentPosition)} • ${formatDuration(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Fullscreen Right
                    IconButton(onClick = { viewModel.toggleFullscreen() }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                // Progress Bar at the very bottom
                PlayerProgressSection(viewModel = viewModel)
            }
        }
    }
}

// formatDuration moved to DurationFormatter.kt


