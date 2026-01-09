package com.github.libretube.test.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.libretube.test.R
import com.github.libretube.test.ui.components.PlaylistCard
import com.github.libretube.test.ui.components.PlaylistCardState
import com.github.libretube.test.ui.components.ShimmerVideoRow
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.theme.LibreTubeTheme

data class HomeScreenState(
    val featured: List<VideoCardState>? = null,
    val continueWatching: List<VideoCardState>? = null,
    val trending: List<VideoCardState>? = null,
    val bookmarks: List<PlaylistCardState>? = null,
    val playlists: List<PlaylistCardState>? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val trendingCategory: String? = null,
    val trendingRegionName: String? = null
)

@Composable
fun HomeScreen(
    state: HomeScreenState,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSectionClick: (String) -> Unit,
    onTrendingCategoryClick: () -> Unit,
    onTrendingRegionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading && !state.isRefreshing) {
        Column {
            repeat(5) { ShimmerVideoRow() }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Featured Section
        state.featured?.takeIf { it.isNotEmpty() }?.let { videos ->
            item {
                HomeSectionHeader(
                    title = stringResource(R.string.featured),
                    onClick = { onSectionClick("featured") }
                )
            }
            item {
                HomeHorizontalVideoList(videos, onVideoClick)
            }
        }

        // Continue Watching Section
        state.continueWatching?.takeIf { it.isNotEmpty() }?.let { videos ->
            item {
                HomeSectionHeader(
                    title = stringResource(R.string.continue_watching),
                    onClick = { onSectionClick("watching") }
                )
            }
            item {
                HomeHorizontalVideoList(videos, onVideoClick)
            }
        }

        // Trending Section
        state.trending?.takeIf { it.isNotEmpty() }?.let { videos ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                         HomeSectionHeader(
                            title = stringResource(R.string.trending),
                            onClick = { onSectionClick("trending") }
                        )
                    }
                    
                    IconButton(onClick = onTrendingCategoryClick) {
                        Icon(painterResource(R.drawable.ic_frame), contentDescription = stringResource(R.string.category))
                    }
                    
                    IconButton(onClick = onTrendingRegionClick) {
                        Icon(painterResource(R.drawable.ic_region), contentDescription = stringResource(R.string.region))
                    }
                }
            }
            item {
                HomeHorizontalVideoList(videos, onVideoClick)
            }
        }

        // Bookmarks Section
        state.bookmarks?.takeIf { it.isNotEmpty() }?.let { playlists ->
            item {
                HomeSectionHeader(
                    title = stringResource(R.string.bookmarks),
                    onClick = { onSectionClick("bookmarks") }
                )
            }
            item {
                HomeHorizontalPlaylistList(playlists, onPlaylistClick)
            }
        }

        // Playlists Section
        state.playlists?.takeIf { it.isNotEmpty() }?.let { playlists ->
            item {
                HomeSectionHeader(
                    title = stringResource(R.string.playlists),
                    onClick = { onSectionClick("playlists") }
                )
            }
            item {
                HomeHorizontalPlaylistList(playlists, onPlaylistClick)
            }
        }
    }
}

@Composable
fun HomeSectionHeader(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HomeHorizontalVideoList(
    videos: List<VideoCardState>,
    onVideoClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(videos) { video ->
            VideoCard(
                state = video,
                onClick = { onVideoClick(video.videoId) },
                modifier = Modifier.width(280.dp) // Fixed width for horizontal scrolling
            )
        }
    }
}

@Composable
fun HomeHorizontalPlaylistList(
    playlists: List<PlaylistCardState>,
    onPlaylistClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists) { playlist ->
            PlaylistCard(
                state = playlist,
                onClick = { onPlaylistClick(playlist.playlistId) },
                modifier = Modifier.width(240.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    LibreTubeTheme {
        HomeScreen(
            state = HomeScreenState(
                featured = listOf(
                    VideoCardState("1", "Hello World", "Channel", "1M views", "10:00", null, null)
                )
            ),
            onVideoClick = {},
            onPlaylistClick = {},
            onSectionClick = {},
            onTrendingCategoryClick = {},
            onTrendingRegionClick = {}
        )
    }
}
