package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.db.obj.WatchHistoryItem
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.NavBarHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.components.PlaylistCardState
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.dialogs.CreatePlaylistDialog
import com.github.libretube.test.ui.dialogs.CreatePlaylistDialog.Companion.CREATE_PLAYLIST_DIALOG_REQUEST_KEY
import com.github.libretube.test.ui.models.LibraryViewModel
import com.github.libretube.test.ui.screens.LibraryScreen
import com.github.libretube.test.ui.screens.LibraryScreenState
import com.github.libretube.test.ui.theme.LibreTubeTheme

class LibraryFragment : Fragment() {

    private val viewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    LibraryContent()
                }
            }
        }
    }

    @Composable
    private fun LibraryContent() {
        val historyItems by viewModel.historyItems.observeAsState(emptyList())
        val historyCount by viewModel.historyCount.observeAsState(0)
        val downloadCount by viewModel.downloadCount.observeAsState(0)
        val playlists by viewModel.playlists.observeAsState(emptyList())
        val bookmarks by viewModel.bookmarks.observeAsState(emptyList())
        val isRefreshing by viewModel.isRefreshing.observeAsState(false)

        val watchHistoryEnabled = remember {
            PreferenceHelper.getBoolean(PreferenceKeys.WATCH_HISTORY_TOGGLE, true)
        }

        val downloadsCardVisible = remember {
            val navBarItems = NavBarHelper.getNavBarItems(requireContext())
            !navBarItems.filter { it.isVisible }.any { it.itemId == R.id.downloadsFragment }
        }

        LibraryScreen(
            state = LibraryScreenState(
                historyItems = historyItems.map { it.toStreamItem().toVideoCardState() },
                historyCount = historyCount,
                downloadCount = downloadCount,
                playlists = playlists.map { it.toPlaylistCardState() },
                bookmarks = bookmarks.map { it.toPlaylistCardState() },
                isRefreshing = isRefreshing,
                watchHistoryEnabled = watchHistoryEnabled,
                downloadsCardVisible = downloadsCardVisible
            ),
            onHistoryClick = {
                findNavController().navigate(R.id.action_libraryFragment_to_watchHistoryFragment)
            },
            onDownloadsClick = {
                findNavController().navigate(R.id.action_libraryFragment_to_downloadsFragment)
            },
            onRecentlyWatchedSeeAll = {
                findNavController().navigate(R.id.action_libraryFragment_to_watchHistoryFragment)
            },
            onPlaylistsSeeAll = {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_libraryListingFragment,
                    bundleOf("type" to LibraryListingFragment.Type.PLAYLISTS.name)
                )
            },
            onBookmarksSeeAll = {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_libraryListingFragment,
                    bundleOf("type" to LibraryListingFragment.Type.BOOKMARKS.name)
                )
            },
            onCreatePlaylistClick = {
                CreatePlaylistDialog()
                    .show(childFragmentManager, CreatePlaylistDialog::class.java.name)
            },
            onVideoClick = { videoId ->
                NavigationHelper.navigateVideo(requireContext(), videoId)
            },
            onPlaylistClick = { playlistId ->
                NavigationHelper.navigatePlaylist(requireContext(), playlistId, PlaylistType.LOCAL)
            },
            onBookmarkClick = { playlistId ->
                NavigationHelper.navigatePlaylist(requireContext(), playlistId, PlaylistType.PUBLIC)
            },
            onRefresh = {
                viewModel.refreshData()
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.refreshData()

        childFragmentManager.setFragmentResultListener(
            CREATE_PLAYLIST_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val isPlaylistCreated = resultBundle.getBoolean(IntentData.playlistTask)
            if (isPlaylistCreated) {
                viewModel.refreshData()
            }
        }
    }

    private fun StreamItem.toVideoCardState() = VideoCardState(
        videoId = url?.toID() ?: "",
        title = title ?: "",
        uploaderName = uploaderName ?: "",
        views = views.formatShort(),
        duration = duration?.let { DateUtils.formatElapsedTime(it) } ?: "",
        thumbnailUrl = thumbnail,
        uploaderAvatarUrl = uploaderAvatar
    )

    private fun PlaylistBookmark.toPlaylistCardState() = PlaylistCardState(
        playlistId = playlistId,
        title = playlistName ?: "",
        description = uploader ?: "",
        videoCount = videos.toLong(),
        thumbnailUrl = thumbnailUrl ?: ""
    )

    private fun Playlists.toPlaylistCardState() = PlaylistCardState(
        playlistId = id ?: "",
        title = name ?: "",
        description = shortDescription ?: "",
        videoCount = videos,
        thumbnailUrl = thumbnail ?: ""
    )
}

