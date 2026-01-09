package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.models.CommonPlayerViewModel
import com.github.libretube.test.ui.models.WatchHistoryModel
import com.github.libretube.test.ui.screens.ClearHistoryDialog
import com.github.libretube.test.ui.screens.WatchHistoryScreen
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.PlayingQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchHistoryFragment : Fragment() {
    private val commonPlayerViewModel: CommonPlayerViewModel by activityViewModels()
    private val viewModel: WatchHistoryModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LibreTubeTheme {
                    WatchHistoryContent()
                }
            }
        }
    }

    @Composable
    private fun WatchHistoryContent() {
        val history by viewModel.filteredWatchHistory.collectAsState()
        val isMiniPlayerVisible by commonPlayerViewModel.isMiniPlayerVisible.observeAsState(false)
        var showClearDialog by remember { mutableStateOf(false) }

        WatchHistoryScreen(
            viewModel = viewModel,
            onBackClick = {
                if (viewModel.isMultiSelectMode.value) {
                    viewModel.toggleMultiSelectMode()
                } else {
                    requireActivity().onBackPressed()
                }
            },
            onItemClick = { item ->
                NavigationHelper.navigateVideo(requireContext(), item.videoId)
            },
            onItemLongClick = { item ->
                val sheet = VideoOptionsBottomSheet()
                sheet.arguments = bundleOf(IntentData.streamItem to item.toStreamItem())
                sheet.show(childFragmentManager, WatchHistoryFragment::class.java.name)
            },
            onClearHistoryClick = {
                showClearDialog = true
            },
            onPlayAllClick = {
                if (history.isNotEmpty()) {
                    PlayingQueue.clear()
                    PlayingQueue.add(*history.reversed().map { it.toStreamItem() }.toTypedArray())
                    NavigationHelper.navigateVideo(
                        requireContext(),
                        history.last().videoId,
                        keepQueue = true
                    )
                }
            },
            isMiniPlayerVisible = isMiniPlayerVisible
        )

        if (showClearDialog) {
            ClearHistoryDialog(
                onConfirm = { alsoClearPositions ->
                    showClearDialog = false
                    lifecycleScope.launch(Dispatchers.IO) {
                        Database.withTransaction {
                            Database.watchHistoryDao().deleteAll()
                            if (alsoClearPositions) Database.watchPositionDao().deleteAll()
                        }
                    }
                },
                onDismiss = { showClearDialog = false }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.fetchNextPage()
    }
}
