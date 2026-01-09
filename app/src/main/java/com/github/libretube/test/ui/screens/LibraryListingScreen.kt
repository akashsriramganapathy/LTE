package com.github.libretube.test.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.ui.models.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListingScreen(
    viewModel: LibraryViewModel,
    isPlaylists: Boolean,
    onBackClick: () -> Unit,
    onItemClick: (String, PlaylistType) -> Unit,
    onOptionsClick: (String, String, PlaylistType) -> Unit,
    onSortClick: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isReorderMode by viewModel.isReorderMode.collectAsState()
    val reorderPlaylists by viewModel.reorderPlaylists.collectAsState()
    val reorderBookmarks by viewModel.reorderBookmarks.collectAsState()

    val currentItems = if (isReorderMode) {
        if (isPlaylists) reorderPlaylists else reorderBookmarks
    } else {
        if (isPlaylists) playlists else bookmarks
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isPlaylists) R.string.playlists else R.string.bookmarks))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (isReorderMode) {
                        IconButton(onClick = { viewModel.saveReorder(isPlaylists) }) {
                            Icon(painterResource(R.drawable.ic_done), contentDescription = stringResource(R.string.save))
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleReorderMode() }) {
                            Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = stringResource(R.string.reorder_playlist))
                        }
                        IconButton(onClick = onSortClick) {
                            Icon(painterResource(R.drawable.ic_filter_sort), contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (currentItems.isEmpty() && !isRefreshing) {
            EmptyLibraryView(isPlaylists)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = rememberLazyListState()
            ) {
                itemsIndexed(
                    items = currentItems,
                    key = { _, item ->
                        if (item is Playlists) "playlist_${item.id}" else "bookmark_${(item as PlaylistBookmark).playlistId}"
                    }
                ) { index, item ->
                    val title = if (item is Playlists) item.name else (item as PlaylistBookmark).playlistName
                    val uploader = if (item is Playlists) null else (item as PlaylistBookmark).uploader
                    val videoCount = if (item is Playlists) item.videos else (item as PlaylistBookmark).videos.toLong()
                    val thumbnail = if (item is Playlists) item.thumbnail else (item as PlaylistBookmark).thumbnailUrl
                    val id = if (item is Playlists) item.id!! else (item as PlaylistBookmark).playlistId
                    val type = if (isPlaylists) PlaylistType.LOCAL else PlaylistType.PUBLIC

                    LibraryItemRow(
                        title = title ?: "",
                        uploader = uploader,
                        videoCount = videoCount,
                        thumbnail = thumbnail,
                        isReorderMode = isReorderMode,
                        onClick = { if (!isReorderMode) onItemClick(id, type) },
                        onOptionsClick = { onOptionsClick(id, title ?: "", type) },
                        onMoveUp = {
                            if (isPlaylists) viewModel.onItemMovePlaylists(index, index - 1)
                            else viewModel.onItemMoveBookmarks(index, index - 1)
                        },
                        onMoveDown = {
                            if (isPlaylists) viewModel.onItemMovePlaylists(index, index + 1)
                            else viewModel.onItemMoveBookmarks(index, index + 1)
                        },
                        isFirst = index == 0,
                        isLast = index == currentItems.size - 1
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryItemRow(
    title: String,
    uploader: String?,
    videoCount: Long,
    thumbnail: String?,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_empty_playlist),
                error = painterResource(R.drawable.ic_empty_playlist)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = videoCount.toString(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            val subText = if (uploader != null) {
                "$uploader • ${stringResource(R.string.videoCount, videoCount)}"
            } else {
                stringResource(R.string.videoCount, videoCount)
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isReorderMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (!isFirst) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                            Icon(painterResource(R.drawable.ic_arrow_up_down), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            IconButton(onClick = onOptionsClick) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
        }
    }
}

@Composable
fun EmptyLibraryView(isPlaylists: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(if (isPlaylists) R.drawable.ic_empty_playlist else R.drawable.ic_bookmark),
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.emptyList),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
