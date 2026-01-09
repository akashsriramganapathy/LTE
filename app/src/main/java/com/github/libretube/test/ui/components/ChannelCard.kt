package com.github.libretube.test.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.ui.theme.LibreTubeTheme

data class ChannelCardState(
    val channelId: String,
    val name: String,
    val details: String, // e.g. "1.2M subscribers"
    val avatarUrl: String?,
    val isSubscribed: Boolean = false,
    val showSubscribeButton: Boolean = false
)

@Composable
fun ChannelCard(
    state: ChannelCardState,
    onClick: () -> Unit,
    onSubscribe: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = state.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (state.showSubscribeButton) {
            Button(onClick = { onSubscribe?.invoke() }) {
                Text(text = if (state.isSubscribed) "Subscribed" else "Subscribe") // TODO: use stringResource(R.string.subscribe)
            }
        }
    }
}

@Preview
@Composable
private fun ChannelCardPreview() {
    LibreTubeTheme {
        ChannelCard(
            state = ChannelCardState(
                channelId = "1",
                name = "LibreTube Official",
                details = "10K Subscribers",
                avatarUrl = null,
                showSubscribeButton = true
            ),
            onClick = {}
        )
    }
}
