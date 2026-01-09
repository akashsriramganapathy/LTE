package com.github.libretube.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.libretube.test.R
import com.github.libretube.test.repo.FeedProgress
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

data class SubscriptionsScreenState(
    val videos: List<SubscriptionItemState>? = null,
    val channelGroups: List<String> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val feedProgress: FeedProgress? = null,
    val isEmpty: Boolean = false
)

sealed class SubscriptionItemState {
    data class Video(val state: VideoCardState) : SubscriptionItemState()
    object AllCaughtUp : SubscriptionItemState()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsScreenState,
    onVideoClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSortFilterClick: () -> Unit,
    onToggleSubsClick: () -> Unit,
    onEditGroupsClick: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscriptions)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSortFilterClick) {
                        Icon(painterResource(R.drawable.ic_filter_sort), contentDescription = "Sort/Filter")
                    }
                    IconButton(onClick = onToggleSubsClick) {
                        Icon(painterResource(R.drawable.ic_subscriptions), contentDescription = "Toggle Subscriptions")
                    }
                    IconButton(onClick = onEditGroupsClick) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Edit Groups")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Channel Groups Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.channelGroups) { index, groupName ->
                    val isSelected = state.selectedGroupIndex == index
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { onGroupClick(index) },
                                onLongClick = { onGroupLongClick(index) }
                            ),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = groupName,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Feed Progress
            state.feedProgress?.let { progress ->
                if (progress.currentProgress < progress.total) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.currentProgress.toFloat() / progress.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${progress.currentProgress}/${progress.total}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (state.isLoading) {
                // TODO: Add shimmer loading
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.emptyList))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.videos ?: emptyList()) { item ->
                        when (item) {
                            is SubscriptionItemState.Video -> {
                                VideoCard(
                                    state = item.state,
                                    onClick = { onVideoClick(item.state.videoId) }
                                )
                            }
                            is SubscriptionItemState.AllCaughtUp -> {
                                AllCaughtUpItem()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllCaughtUpItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_done),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.all_caught_up),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.all_caught_up_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

