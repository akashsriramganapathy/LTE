package com.github.libretube.test.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.github.libretube.test.util.TextUtils
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchHistoryScreen(
    history: List<WatchHistoryItem>,
    selectedStatusFilter: Int,
    onFilterChanged: (Int) -> Unit,
    onItemClick: (WatchHistoryItem) -> Unit,
    onItemLongClick: (WatchHistoryItem) -> Unit,
    onItemDismissed: (WatchHistoryItem) -> Unit,
    onClearHistoryClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onLoadMore: () -> Unit,
    isMiniPlayerVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Load more when scrolled to bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collectLatest { visibleItems ->
            val lastVisibleItem = visibleItems.lastOrNull()
            if (lastVisibleItem != null && lastVisibleItem.index >= history.size - 5) {
                onLoadMore()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val continueSelected = selectedStatusFilter == 0 || selectedStatusFilter == 1
                    val finishedSelected = selectedStatusFilter == 0 || selectedStatusFilter == 2

                    FilterChip(
                        selected = continueSelected,
                        onClick = {
                            val nextContinue = !continueSelected
                            val nextFinished = finishedSelected
                            val result = when {
                                nextContinue && nextFinished -> 0
                                nextContinue -> 1
                                nextFinished -> 2
                                else -> 0 // fallback
                            }
                            onFilterChanged(result)
                        },
                        label = { Text(stringResource(R.string.continue_watching)) }
                    )
                    FilterChip(
                        selected = finishedSelected,
                        onClick = {
                            val nextContinue = continueSelected
                            val nextFinished = !finishedSelected
                            val result = when {
                                nextContinue && nextFinished -> 0
                                nextContinue -> 1
                                nextFinished -> 2
                                else -> 0 // fallback
                            }
                            onFilterChanged(result)
                        },
                        label = { Text(stringResource(R.string.finished)) }
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (history.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = onClearHistoryClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.clear_history))
                    }
                    FloatingActionButton(onClick = onPlayAllClick) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.play_all))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (history.isEmpty()) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = if (isMiniPlayerVisible) 80.dp else 16.dp
                    )
                ) {
                    // Filter Chips as a header or top bar is better, but here I'll use a persistent Row above LazyColumn
                    // Wait, Scaffold topBar is better. I'll move chips there or as a stickyHeader.
                    
                    itemsIndexed(
                        items = history,
                        key = { _, item -> item.videoId }
                    ) { _, item ->
                        WatchHistorySwipeItem(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            onDismissed = { onItemDismissed(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistorySwipeItem(
    item: WatchHistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissed: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDismissed()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                }, label = "dismiss_color"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            val durationText = when {
                item.isLive -> stringResource(R.string.live)
                item.duration != null -> DateUtils.formatElapsedTime(item.duration)
                else -> ""
            }
            
            VideoCard(
                state = VideoCardState(
                    videoId = item.videoId,
                    title = item.title ?: "",
                    uploaderName = item.uploader ?: "",
                    views = item.uploadDate?.let { TextUtils.localizeDate(it) } ?: "",
                    duration = durationText,
                    thumbnailUrl = item.thumbnailUrl ?: "",
                    uploaderAvatarUrl = item.uploaderAvatar
                ),
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            )
        }
    )
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 16.dp)
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
