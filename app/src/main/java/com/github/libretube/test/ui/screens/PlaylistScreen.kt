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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.github.libretube.test.ui.sheets.PlaylistOptionsSheet
import com.github.libretube.test.ui.sheets.SortPlaylistSheet
import com.github.libretube.test.ui.sheets.DownloadPlaylistBottomSheet
import com.github.libretube.test.ui.sheets.RenamePlaylistSheet
import com.github.libretube.test.ui.sheets.EditPlaylistDescriptionSheet
import com.github.libretube.test.ui.sheets.DeletePlaylistConfirmationSheet
import com.github.libretube.test.helpers.ImportHelper
import com.github.libretube.test.enums.ImportFormat
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
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
    onRenamePlaylist: (String) -> Unit,
    onChangeDescription: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isReorderMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Sheets state
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showDownloadSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameSheet by rememberSaveable { mutableStateOf(false) }
    var showDescriptionSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteSheet by rememberSaveable { mutableStateOf(false) }
    var showSongOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var selectedSongItem by remember { mutableStateOf<StreamItem?>(null) }

    // Sort state
    var currentSortOrder by rememberSaveable { mutableStateOf("custom") }
    
    // Manage playlist items locally for filtering and reordering
    var playlistItems by remember(playlist) { mutableStateOf(playlist?.relatedStreams ?: emptyList()) }
    
    // Sort logic
    LaunchedEffect(currentSortOrder, playlist) {
        playlist?.relatedStreams?.let { streams ->
             playlistItems = when (currentSortOrder) {
                "creation_date" -> streams.reversed() // ASSUMPTION: DB is oldest first, newest should be reversed
                "creation_date_reversed" -> streams // Oldest first
                "alphabetic" -> streams.sortedBy { it.title?.lowercase() }
                "alphabetic_reversed" -> streams.sortedByDescending { it.title?.lowercase() }
                else -> streams // custom/default
            }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                 ImportHelper.exportPlaylists(
                    context = context,
                    uri = it,
                    importFormat = ImportFormat.YOUTUBE_COMPATIBLE,
                    selectedPlaylistIds = listOf(playlistId)
                )
            }
        }
    }
    
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
                        if (isReorderMode) {
                            TextButton(onClick = {
                                onSaveReorder(playlistItems)
                                isReorderMode = false
                            }) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            IconButton(onClick = { isSearchVisible = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
                            }
                            IconButton(onClick = { showOptionsSheet = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isReorderMode && !isLoading && playlist != null && filteredItems.isNotEmpty()) {
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
                        playlist = playlist!!,
                        videoCount = playlistItems.size,
                        isBookmarked = isBookmarked,
                        playlistType = playlistType,
                        onBookmarkClick = onBookmarkClick,
                        onReorderClick = { isReorderMode = !isReorderMode },
                        onShuffleClick = onShuffleClick,
                        isReorderEnabled = playlistType != PlaylistType.PUBLIC,
                        firstItemThumbnail = playlistItems.firstOrNull()?.thumbnail
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
                        PlaylistItem(
                            item = item,
                            isReorderMode = isReorderMode,
                            onClick = { onVideoClick(item) },
                            onLongClick = { 
                                selectedSongItem = item
                                showSongOptionsSheet = true
                            },
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
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showOptionsSheet) {
        PlaylistOptionsSheet(
            onPlayBackground = {
                // TODO: Implement Logic to start background play
                 onPlayAllClick() // Temporary
            },
            onDownload = { showDownloadSheet = true },
            onSort = { showSortSheet = true },
            onExport = { 
                exportLauncher.launch("playlist_${playlist?.name ?: "export"}.json")
            },
            onRename = { showRenameSheet = true },
            onChangeDescription = { showDescriptionSheet = true },
            onDelete = { showDeleteSheet = true },
            onDismissRequest = { showOptionsSheet = false }
        )
    }

    if (showSortSheet) {
        SortPlaylistSheet(
            currentSort = currentSortOrder,
            onSortSelected = { 
                currentSortOrder = it
            },
            onDismissRequest = { showSortSheet = false }
        )
    }

    if (showDownloadSheet && playlist != null) {
        DownloadPlaylistBottomSheet(
            playlistId = playlistId,
            playlistName = playlist.name ?: "",
            playlistType = playlistType,
            onDismissRequest = { showDownloadSheet = false }
        )
    }

    if (showRenameSheet && playlist != null) {
        RenamePlaylistSheet(
            currentName = playlist.name ?: "",
            onConfirm = { 
                onRenamePlaylist(it)
                showRenameSheet = false
            },
            onCancel = { showRenameSheet = false }
        )
    }

    if (showDescriptionSheet && playlist != null) {
        EditPlaylistDescriptionSheet(
            currentDescription = playlist.description ?: "",
            onConfirm = {
                onChangeDescription(it)
                showDescriptionSheet = false
            },
            onCancel = { showDescriptionSheet = false }
        )
    }

    if (showDeleteSheet) {
        DeletePlaylistConfirmationSheet(
            onConfirm = {
                onDeletePlaylist()
                showDeleteSheet = false
            },
            onCancel = { showDeleteSheet = false }
        )
    }

    if (showSongOptionsSheet && selectedSongItem != null) {
        com.github.libretube.test.ui.sheets.VideoOptionsSheet(
            streamItem = selectedSongItem!!,
            onDismissRequest = { showSongOptionsSheet = false },
            onShareClick = {
                // Share functionality
            },
            onDownloadClick = {
                // TODO: Implement download
            }
        )
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
    isReorderEnabled: Boolean,
    firstItemThumbnail: String? = null
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1:1 Square Cover
            Card(
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                AsyncImage(
                    model = firstItemThumbnail ?: playlist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (playlist.uploader != null) 
                        stringResource(R.string.uploaderAndVideoCount, playlist.uploader!!, videoCount)
                    else 
                        stringResource(R.string.videoCount, videoCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
