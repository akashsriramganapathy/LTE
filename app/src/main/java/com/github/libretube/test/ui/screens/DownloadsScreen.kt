package com.github.libretube.test.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.DownloadWithItems
import com.github.libretube.test.db.obj.filterByTab
import com.github.libretube.test.extensions.formatAsFileSize
import com.github.libretube.test.helpers.DownloadHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.obj.DownloadStatus
import com.github.libretube.test.services.DownloadService
import com.github.libretube.test.ui.fragments.DownloadTab
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf<DownloadTab?>(null) } // null = ALL
    var sortType by remember {
        mutableIntStateOf(PreferenceHelper.getInt(PreferenceKeys.SELECTED_DOWNLOAD_SORT_TYPE, 0))
    }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    // Service binding
    val downloadService = rememberDownloadService()
    val progressMap = remember { mutableStateMapOf<Int, DownloadStatus>() }

    // Collect progress updates
    LaunchedEffect(downloadService) {
        downloadService?.getService()?.downloadFlow?.collectLatest { (id, status) ->
            progressMap[id] = status
        }
    }

    // Data flows
    val downloads by when (selectedTab) {
        DownloadTab.PLAYLIST -> DatabaseHolder.Database.downloadDao()
            .getDownloadPlaylistsFlow()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        else -> DatabaseHolder.Database.downloadDao()
            .getAllFlow()
            .collectAsStateWithLifecycle(initialValue = emptyList())
    }

    val filteredDownloads = if (selectedTab == null || selectedTab == DownloadTab.ALL) {
        downloads as? List<DownloadWithItems> ?: emptyList()
    } else if (selectedTab == DownloadTab.PLAYLIST) {
        emptyList() // Handle playlists separately
    } else {
        (downloads as? List<DownloadWithItems>)?.filterByTab(selectedTab!!) ?: emptyList()
    }

    val sortedDownloads = if (sortType == 0) filteredDownloads else filteredDownloads.reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
                actions = {
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Sort")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == null,
                    onClick = { selectedTab = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                ) {
                    Text("All")
                }
                SegmentedButton(
                    selected = selectedTab == DownloadTab.VIDEO,
                    onClick = { selectedTab = DownloadTab.VIDEO },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                ) {
                    Text("Video")
                }
                SegmentedButton(
                    selected = selectedTab == DownloadTab.AUDIO,
                    onClick = { selectedTab = DownloadTab.AUDIO },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                ) {
                    Text("Audio")
                }
                SegmentedButton(
                    selected = selectedTab == DownloadTab.PLAYLIST,
                    onClick = { selectedTab = DownloadTab.PLAYLIST },
                    shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                ) {
                    Text("Playlists")
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Shuffle all
                        com.github.libretube.test.helpers.BackgroundHelper.playOnBackgroundOffline(
                            context,
                            null,
                            selectedTab ?: DownloadTab.ALL,
                            shuffle = true
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shuffle")
                }

                OutlinedButton(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete All")
                }
            }

            // Downloads list
            if (sortedDownloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (selectedTab) {
                            DownloadTab.PLAYLIST -> stringResource(R.string.no_playlists)
                            else -> stringResource(R.string.no_downloads)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedDownloads, key = { it.download.videoId }) { download ->
                        val downloadItemId = download.downloadItems.firstOrNull()?.id ?: 0
                        DownloadItemCard(
                            download = download,
                            progress = progressMap[downloadItemId],
                            onResume = {
                                downloadService?.getService()?.resume(downloadItemId)
                            },
                            onPause = {
                                downloadService?.getService()?.pause(downloadItemId)
                            },
                            onDelete = {
                                // Show delete dialog
                            },
                            onClick = {
                                onNavigateToVideo(download.download.videoId)
                            }
                        )
                    }
                }
            }
        }
    }

    // Sort dialog
    if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = { Text("Sort") },
            text = {
                Column {
                    val options = arrayOf("Newest first", "Oldest first")
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortType == index,
                                onClick = {
                                    sortType = index
                                    PreferenceHelper.putInt(
                                        PreferenceKeys.SELECTED_DOWNLOAD_SORT_TYPE,
                                        index
                                    )
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.okay))
                }
            }
        )
    }

    // Delete all dialog
    if (showDeleteAllDialog) {
        var deleteOnlyWatched by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteOnlyWatched,
                        onCheckedChange = { deleteOnlyWatched = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_only_watched_videos))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Delete all logic
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadWithItems,
    progress: DownloadStatus?,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier.size(width = 120.dp, height = 68.dp)
            ) {
                AsyncImage(
                    model = download.download.thumbnailPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                // Progress overlay
                when (progress) {
                    is DownloadStatus.Progress -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress.progress / 100f },
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    is DownloadStatus.Queued -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = download.download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )

                when (progress) {
                    is DownloadStatus.Progress -> {
                        Text(
                            text = "${progress.downloaded.formatAsFileSize()} / ${progress.total.formatAsFileSize()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadStatus.Queued -> {
                        Text(
                            text = "Queued",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadStatus.Stalled -> {
                        Text(
                            text = "Stalled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = download.download.uploader ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (progress) {
                    is DownloadStatus.Progress, is DownloadStatus.Queued -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Pause")
                        }
                    }
                    else -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun rememberDownloadService(): DownloadService.LocalBinder? {
    var binder by remember { mutableStateOf<DownloadService.LocalBinder?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service as? DownloadService.LocalBinder
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }

        if (DownloadService.IS_DOWNLOAD_RUNNING) {
            val intent = Intent(context, DownloadService::class.java)
            context.bindService(intent, connection, 0)
        }

        onDispose {
            runCatching { context.unbindService(connection) }
        }
    }

    return binder
}
