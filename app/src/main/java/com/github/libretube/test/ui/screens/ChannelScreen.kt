package com.github.libretube.test.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.SubscriptionHelper
import com.github.libretube.test.api.obj.Channel
import com.github.libretube.test.api.obj.ChannelTab
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.helpers.ClipboardHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.components.VideoCard
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.models.sources.ChannelTabPagingSource
import com.github.libretube.test.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelData: Channel?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onShowOptions: () -> Unit,
    onVideoClick: (StreamItem) -> Unit,
    onVideoLongClick: (StreamItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    
    // Subscription state
    var isSubscribed by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(PreferenceHelper.getBoolean(PreferenceKeys.NOTIFICATION_ENABLED, true))
    }
    
    // Check subscription status
    LaunchedEffect(channelData?.id) {
        channelData?.id?.let { channelId ->
            isSubscribed = withContext(Dispatchers.IO) {
                SubscriptionHelper.isSubscribed(channelId) ?: false
            }
        }
    }
    
    val tabs = remember(channelData) {
        if (channelData == null) emptyList()
        else listOf(ChannelTab("Videos", "")) + channelData.tabs
    }
    
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    if (channelData != null) {
                        Column {
                            Text(
                                text = channelData.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    R.string.subscribers,
                                    channelData.subscriberCount.formatShort()
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowOptions) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (channelData != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Channel header
                ChannelHeader(
                    channel = channelData,
                    isSubscribed = isSubscribed,
                    notificationsEnabled = notificationsEnabled,
                    onSubscribeClick = {
                        scope.launch(Dispatchers.IO) {
                            if (isSubscribed) {
                                SubscriptionHelper.unsubscribe(channelData.id!!)
                            } else {
                                SubscriptionHelper.subscribe(
                                    channelData.id!!,
                                    channelData.name ?: "",
                                    channelData.avatarUrl,
                                    channelData.verified
                                )
                            }
                            isSubscribed = !isSubscribed
                        }
                    },
                    onNotificationClick = {
                        // Toggle notification for this channel
                    },
                    onBannerClick = {
                        channelData.bannerUrl?.let {
                            NavigationHelper.openImagePreview(context, it)
                        }
                    },
                    onAvatarClick = {
                        channelData.avatarUrl?.let {
                            NavigationHelper.openImagePreview(context, it)
                        }
                    },
                    onNameLongClick = {
                        ClipboardHelper.save(context, text = channelData.name ?: "")
                    }
                )

                // Tabs
                if (tabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = { Text(tab.name) }
                            )
                        }
                    }
                }

                // Content pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val tab = tabs[page]
                    ChannelTabContent(
                        tab = tab,
                        channelId = channelData.id ?: "",
                        initialVideos = if (page == 0) channelData.relatedStreams else emptyList(),
                        initialNextPage = if (page == 0) channelData.nextpage else null,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = onVideoLongClick
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelHeader(
    channel: Channel,
    isSubscribed: Boolean,
    notificationsEnabled: Boolean,
    onSubscribeClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onBannerClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onNameLongClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Banner
        if (channel.bannerUrl != null) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable(onClick = onBannerClick),
                contentScale = ContentScale.Crop
            )
        }

        // Avatar and info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clickable(onClick = onAvatarClick)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onNameLongClick)
                ) {
                    Text(
                        text = channel.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.verified) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_verified),
                            contentDescription = "Verified",
                            modifier = Modifier
                                .size(20.dp)
                                .padding(start = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (!channel.description.isNullOrBlank()) {
                    Text(
                        text = channel.description.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Subscribe and notification buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSubscribeClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isSubscribed) 
                        stringResource(R.string.unsubscribe) 
                    else 
                        stringResource(R.string.subscribe)
                )
            }

            if (isSubscribed && notificationsEnabled) {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications"
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelTabContent(
    tab: ChannelTab,
    channelId: String,
    initialVideos: List<StreamItem>,
    initialNextPage: String?,
    onVideoClick: (StreamItem) -> Unit,
    onVideoLongClick: (StreamItem) -> Unit
) {
    val context = LocalContext.current

    // If tab has no data (Videos tab), use manual pagination
    if (tab.data.isEmpty()) {
        var videos by remember { mutableStateOf(initialVideos) }
        var nextPage by remember { mutableStateOf(initialNextPage) }
        var isLoadingMore by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No videos available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos, key = { it.url.orEmpty() }) { stream ->
                    VideoCard(
                        state = VideoCardState(
                            videoId = stream.url?.substringAfterLast("/") ?: "",
                            title = stream.title ?: "",
                            uploaderName = stream.uploaderName ?: "",
                            views = TextUtils.formatViewsString(
                                context,
                                stream.views ?: -1L,
                                stream.uploaded ?: 0L
                            ),
                            duration = stream.duration?.let {
                                android.text.format.DateUtils.formatElapsedTime(it)
                            } ?: "",
                            thumbnailUrl = stream.thumbnail,
                            uploaderAvatarUrl = stream.uploaderAvatar
                        ),
                        onClick = { onVideoClick(stream) },
                        onLongClick = { onVideoLongClick(stream) }
                    )
                }

                // Load more indicator
                if (nextPage != null && !isLoadingMore) {
                    item {
                        LaunchedEffect(Unit) {
                            if (!isLoadingMore) {
                                isLoadingMore = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val response = MediaServiceRepository.instance
                                            .getChannelNextPage(channelId, nextPage!!)
                                        withContext(Dispatchers.Main) {
                                            videos = videos + response.relatedStreams
                                            nextPage = response.nextpage
                                        }
                                    } catch (e: Exception) {
                                        // Handle error
                                    } finally {
                                        isLoadingMore = false
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    } else {
        // Use Paging 3 for tabs with data
        val pagingFlow = remember(tab) {
            Pager(
                PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = { ChannelTabPagingSource(tab) }
            ).flow
        }
        
        val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()

        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lazyPagingItems.itemCount) { index ->
                lazyPagingItems[index]?.let { contentItem ->
                    // ContentItem can be StreamItem, ChannelItem, or PlaylistItem
                    val streamItem = contentItem as? StreamItem
                    if (streamItem != null) {
                        VideoCard(
                            state = VideoCardState(
                                videoId = streamItem.url?.substringAfterLast("/") ?: "",
                                title = streamItem.title ?: "",
                                uploaderName = streamItem.uploaderName ?: "",
                                views = TextUtils.formatViewsString(
                                    context,
                                    streamItem.views ?: -1L,
                                    streamItem.uploaded ?: 0L
                                ),
                                duration = streamItem.duration?.let {
                                    android.text.format.DateUtils.formatElapsedTime(it)
                                } ?: "",
                                thumbnailUrl = (streamItem as com.github.libretube.test.api.obj.StreamItem).thumbnail,
                                uploaderAvatarUrl = streamItem.uploaderAvatar
                            ),
                            onClick = { onVideoClick(streamItem) },
                            onLongClick = { onVideoLongClick(streamItem) }
                        )
                    }
                    // TODO: Handle other content types (ChannelItem, PlaylistItem)
                }
            }
        }
    }
}
