package com.github.libretube.test.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.libretube.test.R
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.components.PlaylistCardState
import com.github.libretube.test.ui.components.LibraryShelfItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

data class LibraryScreenState(
    val historyItems: List<VideoCardState> = emptyList(),
    val historyCount: Int = 0,
    val downloadCount: Int = 0,
    val playlists: List<PlaylistCardState> = emptyList(),
    val bookmarks: List<PlaylistCardState> = emptyList(),
    val isRefreshing: Boolean = false,
    val watchHistoryEnabled: Boolean = true,
    val downloadsCardVisible: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onRecentlyWatchedSeeAll: () -> Unit,
    onPlaylistsSeeAll: () -> Unit,
    onBookmarksSeeAll: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library)) }
            )
        }
    ) { padding ->
        val pullRefreshState = rememberPullToRefreshState()
        
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Quick Access Dashboard
                    item {
                        DashboardSection(
                            state = state,
                            onHistoryClick = onHistoryClick,
                            onDownloadsClick = onDownloadsClick
                        )
                    }

                    // Recently Watched
                    if (state.watchHistoryEnabled && state.historyItems.isNotEmpty()) {
                        item {
                            LibrarySectionHeader(
                                title = stringResource(R.string.recently_watched),
                                onSeeAllClick = onRecentlyWatchedSeeAll
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.historyItems) { video ->
                                    VideoCard(
                                        state = video,
                                        onClick = { onVideoClick(video.videoId) },
                                        modifier = Modifier.width(280.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Playlists
                    item {
                        LibrarySectionHeader(
                            title = stringResource(R.string.playlists),
                            onSeeAllClick = onPlaylistsSeeAll,
                            onAddClick = onCreatePlaylistClick
                        )
                    }
                    if (state.playlists.isEmpty()) {
                        item { EmptyLibraryState() }
                    } else {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.playlists) { playlist ->
                                    LibraryShelfItem(
                                        state = playlist,
                                        onClick = { onPlaylistClick(playlist.playlistId) }
                                    )
                                }
                            }
                        }
                    }

                    // Bookmarks
                    if (state.bookmarks.isNotEmpty()) {
                        item {
                            LibrarySectionHeader(
                                title = stringResource(R.string.bookmarks),
                                onSeeAllClick = onBookmarksSeeAll
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.bookmarks) { bookmark ->
                                    LibraryShelfItem(
                                        state = bookmark,
                                        onClick = { onBookmarkClick(bookmark.playlistId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSection(
    state: LibraryScreenState,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.watchHistoryEnabled) {
            DashboardCard(
                title = stringResource(R.string.watch_history),
                count = stringResource(R.string.count_videos, state.historyCount),
                icon = R.drawable.ic_time_outlined,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onHistoryClick,
                modifier = Modifier.weight(1f)
            )
        }
        
        if (state.downloadsCardVisible) {
            DashboardCard(
                title = stringResource(R.string.downloads),
                count = stringResource(R.string.count_files, state.downloadCount),
                icon = R.drawable.ic_download,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onDownloadsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    count: String,
    icon: Int,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun LibrarySectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    onAddClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        
        if (onAddClick != null) {
            IconButton(onClick = onAddClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.tooltip_create_playlist),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        TextButton(onClick = onSeeAllClick) {
            Text(
                text = stringResource(R.string.see_all),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_list),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = stringResource(R.string.emptyList),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
