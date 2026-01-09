package com.github.libretube.test.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.api.obj.ChapterSegment
import com.github.libretube.test.ui.components.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    chapters: List<ChapterSegment>,
    onChapterClick: (ChapterSegment) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn {
                items(chapters) { chapter ->
                    ChapterItem(chapter = chapter, onClick = { onChapterClick(chapter) })
                }
            }
        }
    }
}

@Composable
fun ChapterItem(chapter: ChapterSegment, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!chapter.image.isNullOrEmpty()) {
            AsyncImage(
                model = chapter.image,
                contentDescription = null,
                modifier = Modifier
                    .width(100.dp)
                    .height(56.dp)
                    .padding(end = 8.dp)
            )
        }
        
        Text(
            text = formatTime(chapter.start * 1000), // convert seconds to millis
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(50.dp)
        )
        
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
