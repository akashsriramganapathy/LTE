package com.github.libretube.test.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.ui.models.PlayerViewModel
import com.github.libretube.test.ui.models.PlayerCommandEvent

@Composable
fun PlayerMetadataSection(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uploader by viewModel.uploader.collectAsState()
    val uploaderAvatar by viewModel.uploaderAvatar.collectAsState()
    val subscriberCount by viewModel.subscriberCount.collectAsState()
    val description by viewModel.description.collectAsState()
    val views by viewModel.views.collectAsState()
    val likes by viewModel.likes.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val isDisliked by viewModel.isDisliked.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val isAudioOnly by viewModel.isAudioOnlyMode.collectAsState()
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        // Uploader Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = uploaderAvatar,
                contentDescription = "Uploader Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uploader,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subscriberCount != null) {
                    Text(
                        text = "${formatCount(subscriberCount!!)} subscribers",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            ActionButton(
                icon = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                label = if (isSubscribed) "Subscribed" else "Subscribe",
                tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.toggleSubscription() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Share,
                label = "Share",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.triggerPlayerCommand(PlayerCommandEvent.Share) }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FileDownload,
                label = "Download",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.triggerPlayerCommand(PlayerCommandEvent.Download) }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PlaylistAdd,
                label = "Save",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.triggerPlayerCommand(PlayerCommandEvent.SaveToPlaylist) }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = "Bookmark",
                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.toggleBookmark() }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = if (isAudioOnly) Icons.Default.MusicNote else Icons.Default.MusicOff,
                label = if (isAudioOnly) "Audio: On" else "Audio: Off",
                tint = if (isAudioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { viewModel.toggleAudioOnlyMode() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Action Buttons (Like/Dislike - Data Only)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { viewModel.toggleLike() },
                label = { Text(formatCount(likes + (if (isLiked) 1 else 0))) },
                leadingIcon = {
                    Icon(
                        if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                border = null,
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = { viewModel.toggleDislike() },
                label = { Text("Dislike") },
                leadingIcon = { 
                    Icon(
                        if (isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isDisliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                border = null,
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Description Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatCount(views)} views",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isDescriptionExpanded && description.length > 100) {
                    Text(
                        text = "Show more",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}


