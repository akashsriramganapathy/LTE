package com.github.libretube.test.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.libretube.test.ui.models.PlayerViewModel

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val title by viewModel.title.collectAsState()
    val uploader by viewModel.uploader.collectAsState()
    
    // Local state for seeking to ensure smooth slider movement
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val sliderPosition = if (isSeeking) seekPosition else currentPosition.toFloat()
    val sliderDuration = duration.toFloat().coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Controls (Close, Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* Collapse handled by parent via Draggable */ }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
            }
            IconButton(onClick = { /* Settings */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        
        // Metadata
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(uploader, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }

        // Center Controls (Play/Pause, Nex/Prev)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(32.dp))
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.width(32.dp))
            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White)
            }
        }

        // Bottom Controls (Seekbar, Time)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(sliderPosition.toLong()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = sliderPosition,
                onValueChange = { 
                    isSeeking = true
                    seekPosition = it
                },
                onValueChangeFinished = {
                    isSeeking = false
                    viewModel.seekTo(seekPosition.toLong())
                },
                valueRange = 0f..sliderDuration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.LightGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
