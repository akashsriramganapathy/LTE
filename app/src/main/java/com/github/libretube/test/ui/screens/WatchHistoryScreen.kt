package com.github.libretube.test.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.libretube.test.R
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.models.WatchHistoryModel
import kotlinx.coroutines.flow.collectLatest
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchHistoryScreen(
    viewModel: WatchHistoryModel,
    onBackClick: () -> Unit,
    onItemClick: (WatchHistoryItem) -> Unit,
    onItemLongClick: (WatchHistoryItem) -> Unit,
    onClearHistoryClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    isMiniPlayerVisible: Boolean
) {
    val history by viewModel.filteredWatchHistory.collectAsState()
    val groupedHistory by viewModel.groupedWatchHistory.collectAsState()
    val selectedStatusFilter by viewModel.selectedStatus.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    
    val listState = rememberLazyListState()

    // Loading next page
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collectLatest { visibleItems ->
            val lastVisibleItem = visibleItems.lastOrNull()
            if (lastVisibleItem != null && lastVisibleItem.index >= history.size - 5) {
                viewModel.fetchNextPage()
            }
        }
    }

    if (isMultiSelectMode) {
        BackHandler {
            viewModel.toggleMultiSelectMode()
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = { Text("${selectedItems.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleMultiSelectMode() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.deleteSelectedItems() }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.watch_history)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = onClearHistoryClick) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                        }
                        IconButton(onClick = onPlayAllClick) {
                            Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode && !isMiniPlayerVisible && history.isNotEmpty()) {
                FloatingActionButton(onClick = onPlayAllClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (history.isEmpty()) {
                WatchHistoryEmptyView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (isMiniPlayerVisible) 80.dp else 16.dp)
                ) {
                    // Filter Chips as first item
                    item {
                        HistoryFilters(
                            selectedFilter = selectedStatusFilter,
                            onFilterChanged = { viewModel.selectedStatusFilter = it }
                        )
                    }

                    groupedHistory.forEach { (date, items) ->
                        // The key used here is "date", which is unique per day.
                        stickyHeader(key = date) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                            ) {
                                Text(
                                    text = date,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        itemsIndexed(
                            items = items,
                            key = { _, item -> item.videoId }
                        ) { _, item ->
                            val isSelected = selectedItems.contains(item.videoId)
                            
                            WatchHistorySwipeItem(
                                item = item,
                                isSelected = isSelected,
                                isMultiSelectMode = isMultiSelectMode,
                                onClick = {
                                    if (isMultiSelectMode) viewModel.toggleItemSelection(item.videoId)
                                    else onItemClick(item)
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        viewModel.toggleMultiSelectMode()
                                        viewModel.toggleItemSelection(item.videoId)
                                    } else {
                                        onItemLongClick(item)
                                    }
                                },
                                onDismissed = { viewModel.removeFromHistory(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilters(
    selectedFilter: Int,
    onFilterChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == 1 || selectedFilter == 0,
            onClick = {
                val next = when (selectedFilter) {
                    0 -> 2
                    1 -> 0
                    2 -> 0
                    else -> 0
                }
                onFilterChanged(next)
            },
            label = { Text(stringResource(R.string.continue_watching)) }
        )
        FilterChip(
            selected = selectedFilter == 2 || selectedFilter == 0,
            onClick = {
                val next = when (selectedFilter) {
                    0 -> 1
                    1 -> 0
                    2 -> 0
                    else -> 0
                }
                onFilterChanged(next)
            },
            label = { Text(stringResource(R.string.finished)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistorySwipeItem(
    item: WatchHistoryItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissed: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDismissed()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error
                else Color.Transparent, label = ""
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = if (isMultiSelectMode) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() }
                    )
                }
                
                VideoCard(
                    state = VideoCardState(
                        videoId = item.videoId,
                        title = item.title ?: "",
                        uploaderName = item.uploader ?: "",
                        views = "", 
                        duration = item.duration?.let { DateUtils.formatElapsedTime(it) } ?: "",
                        thumbnailUrl = item.thumbnailUrl ?: "",
                        uploaderAvatarUrl = item.uploaderAvatar
                    ),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    )
}

@Composable
fun WatchHistoryEmptyView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ClearHistoryDialog(
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var clearWatchPositions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_history)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { clearWatchPositions = !clearWatchPositions }
            ) {
                Checkbox(
                    checked = clearWatchPositions,
                    onCheckedChange = { clearWatchPositions = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.also_clear_watch_positions))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clearWatchPositions) }) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
