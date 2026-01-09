package com.github.libretube.test.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.ui.theme.LibreTubeTheme

data class VideoCardState(
    val videoId: String,
    val title: String,
    val uploaderName: String,
    val views: String,
    val duration: String,
    val thumbnailUrl: String?,
    val uploaderAvatarUrl: String?,
    val watchProgress: Float? = null // 0.0 to 1.0
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    state: VideoCardState,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(vertical = 8.dp, horizontal = 12.dp) // ItemRow style padding aprox
    ) {
        // Thumbnail Section (45% width approx, but let's use weight)
        Box(
            modifier = Modifier
                .weight(0.45f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black) // Placeholder
        ) {
            AsyncImage(
                model = state.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            // Duration Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            
            // Watch Progress
            state.watchProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info Section
        Column(
            modifier = Modifier
                .weight(0.55f)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = state.views,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Channel Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.uploaderAvatarUrl != null) {
                    AsyncImage(
                        model = state.uploaderAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = state.uploaderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
private fun VideoCardPreview() {
    LibreTubeTheme {
        VideoCard(
            state = VideoCardState(
                videoId = "123",
                title = "Amazing Video Title That Is Very Long And Wraps To Two Lines",
                views = "1.2M views • 2 days ago",
                duration = "10:05",
                thumbnailUrl = "",
                uploaderName = "Uploader Name",
                uploaderAvatarUrl = ""
            ),
            onClick = {}
        )
    }
}
