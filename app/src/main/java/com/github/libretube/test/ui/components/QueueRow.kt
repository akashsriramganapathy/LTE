package com.github.libretube.test.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.ui.theme.LibreTubeTheme

data class QueueRowState(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String
)

@Composable
fun QueueRow(
    state: QueueRowState,
    onClick: () -> Unit,
    onDrag: (() -> Unit)? = null, // In future, use Modifier.dragAndDropSource
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = state.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(80.dp)
                .height(45.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), // 16sp bold
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = state.author,
                style = MaterialTheme.typography.bodySmall, // 12sp
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Drag Handle
        Icon(
            painter = painterResource(id = R.drawable.ic_drag),
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun QueueRowPreview() {
    LibreTubeTheme {
        QueueRow(
            state = QueueRowState(
                videoId = "1",
                title = "Awesome Track",
                author = "Artist Name",
                thumbnailUrl = ""
            ),
            onClick = {}
        )
    }
}
