package com.github.libretube.test.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Playlist
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.util.TextUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    playlist: Playlist?,
    playlistType: PlaylistType,
    isLoading: Boolean,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    onVideoClick: (StreamItem) -> Unit,
    onVideoLongClick: (StreamItem) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSaveReorder: (List<StreamItem>) -> Unit,
    onDeleteVideo: (StreamItem) -> Unit,
    onShowOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isReorderMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    
    // Manage playlist items locally for filtering and reordering
    var playlistItems by remember(playlist) { mutableStateOf(playlist?.relatedStreams ?: emptyList()) }
    
    // Filtered list
    val filteredItems by remember(playlistItems, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) playlistItems
            else playlistItems.filter {
                it.title?.contains(searchQuery, ignoreCase = true) == true ||
                it.uploaderName?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            if (isSearchVisible) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchVisible = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.playlists)) },
                    actions = {
                        IconButton(onClick = { isSearchVisible = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        IconButton(onClick = onShowOptions) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (isReorderMode) {
                FloatingActionButton(
                    onClick = {
                        onSaveReorder(playlistItems)
                        isReorderMode = false
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
                }
            } else if (!isLoading && playlist != null && filteredItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onPlayAllClick
                ) {
                    Icon(painter = painterResource(R.drawable.ic_playlist), contentDescription = stringResource(R.string.play_all))
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlist != null) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 88.dp), // Space for FAB
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header
                item {
                    PlaylistHeader(
                        playlist = playlist,
                        videoCount = playlistItems.size,
                        isBookmarked = isBookmarked,
                        playlistType = playlistType,
                        onBookmarkClick = onBookmarkClick,
                        onReorderClick = { isReorderMode = !isReorderMode },
                        onShuffleClick = onShuffleClick,
                        isReorderEnabled = playlistType != PlaylistType.PUBLIC
                    )
                }

                if (filteredItems.isEmpty()) {
                    item {
                         Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                             Text(text = stringResource(R.string.emptyList))
                         }
                    }
                } else {
                    itemsIndexed(
                        items = filteredItems,
                        key = { _, item -> item.url + item.title } // Use unique key
                    ) { index, item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    onDeleteVideo(item)
                                    // Optimistic update
                                    playlistItems = playlistItems.toMutableList().apply { remove(item) }
                                    true
                                } else false
                            }
                        )
                        
                        // Swipe enabled only for local playlists and filteredList matches actual list
                        val isSwipeEnabled = playlistType != PlaylistType.PUBLIC && searchQuery.isEmpty() && !isReorderMode

                        if (isSwipeEnabled) {
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                                PlaylistItem(
                                    item = item,
                                    isReorderMode = isReorderMode,
                                    onClick = { onVideoClick(item) },
                                    onLongClick = { onVideoLongClick(item) },
                                    onMoveUp = {
                                        if (index > 0) {
                                            val newList = playlistItems.toMutableList()
                                            java.util.Collections.swap(newList, index, index - 1)
                                            playlistItems = newList
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < playlistItems.lastIndex) {
                                            val newList = playlistItems.toMutableList()
                                            java.util.Collections.swap(newList, index, index + 1)
                                            playlistItems = newList
                                        }
                                    }
                                )
                            }
                        } else {
                            PlaylistItem(
                                item = item,
                                isReorderMode = isReorderMode,
                                onClick = { onVideoClick(item) },
                                onLongClick = { onVideoLongClick(item) },
                                onMoveUp = {
                                    if (index > 0) {
                                        val newList = playlistItems.toMutableList()
                                        java.util.Collections.swap(newList, index, index - 1)
                                        playlistItems = newList
                                    }
                                },
                                onMoveDown = {
                                    if (index < playlistItems.lastIndex) {
                                        val newList = playlistItems.toMutableList()
                                        java.util.Collections.swap(newList, index, index + 1)
                                        playlistItems = newList
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistHeader(
    playlist: Playlist,
    videoCount: Int,
    isBookmarked: Boolean,
    playlistType: PlaylistType,
    onBookmarkClick: () -> Unit,
    onReorderClick: () -> Unit,
    onShuffleClick: () -> Unit,
    isReorderEnabled: Boolean
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = playlist.name ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.uploader != null) 
                        stringResource(R.string.uploaderAndVideoCount, playlist.uploader!!, videoCount)
                    else 
                        stringResource(R.string.videoCount, videoCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Bookmark/Shuffle button
            if (playlistType == PlaylistType.PUBLIC) {
                TextButton(onClick = onBookmarkClick) {
                    Icon(
                        painter = painterResource(if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outlined),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBookmarked) "Bookmarked" else "Bookmark")
                }
            } else {
                TextButton(onClick = onShuffleClick) {
                    Icon(painter = painterResource(R.drawable.ic_shuffle), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.shuffle))
                }
            }

            // Reorder button (only local playlists)
            if (isReorderEnabled) {
                TextButton(onClick = onReorderClick) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reorder")
                }
            }
        }
        
        if (!playlist.description.isNullOrBlank()) {
            Text(
                text = playlist.description!!,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        HorizontalDivider()
    }
}

@Composable
fun PlaylistItem(
    item: StreamItem,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // Add onLongClick handling with combinedModifiers if needed
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reorder controls
        if (isReorderMode) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                }
                Icon(
                    Icons.Default.Menu, 
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                }
            }
        }
        
        Box(modifier = Modifier.width(160.dp).aspectRatio(16f/9f)) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            item.duration?.let {
                Text(
                    text = DateUtils.formatElapsedTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(4.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = item.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploaderName ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = TextUtils.formatViewsString(context, item.views ?: -1L, item.uploaded ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (!isReorderMode) {
            IconButton(onClick = onLongClick) { // Using standard options menu behavior
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        }
    }
}
