package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHelper
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.obj.SelectableOption
import com.github.libretube.test.ui.adapters.VideoCardsAdapter
import com.github.libretube.test.ui.components.VideoCardState
import com.github.libretube.test.ui.models.EditChannelGroupsModel
import com.github.libretube.test.ui.models.SubscriptionsViewModel
import com.github.libretube.test.ui.screens.SubscriptionItemState
import com.github.libretube.test.ui.screens.SubscriptionsScreen
import com.github.libretube.test.ui.screens.SubscriptionsScreenState
import com.github.libretube.test.ui.sheets.ChannelGroupsSheet
import com.github.libretube.test.ui.sheets.FilterSortBottomSheet
import com.github.libretube.test.ui.sheets.FilterSortBottomSheet.Companion.FILTER_SORT_REQUEST_KEY
import com.github.libretube.test.ui.sheets.SubscriptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.PlayingQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionsFragment : Fragment() {

    private val viewModel: SubscriptionsViewModel by activityViewModels()
    private val channelGroupsModel: EditChannelGroupsModel by activityViewModels()

    private var selectedFilterGroup: Int
        set(value) = PreferenceHelper.putInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, value)
        get() = PreferenceHelper.getInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, 0)

    private var selectedSortOrder = PreferenceHelper.getInt(PreferenceKeys.FEED_SORT_ORDER, 0)
        set(value) {
            PreferenceHelper.putInt(PreferenceKeys.FEED_SORT_ORDER, value)
            field = value
        }

    private var hideWatched = PreferenceHelper.getBoolean(PreferenceKeys.HIDE_WATCHED_FROM_FEED, false)
        set(value) {
            PreferenceHelper.putBoolean(PreferenceKeys.HIDE_WATCHED_FROM_FEED, value)
            field = value
        }

    private var showUpcoming = PreferenceHelper.getBoolean(PreferenceKeys.SHOW_UPCOMING_IN_FEED, true)
        set(value) {
            PreferenceHelper.putBoolean(PreferenceKeys.SHOW_UPCOMING_IN_FEED, value)
            field = value
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    SubscriptionsContent()
                }
            }
        }
    }

    @Composable
    private fun SubscriptionsContent() {
        val videoFeed by viewModel.videoFeed.observeAsState()
        val feedProgress by viewModel.feedProgress.observeAsState()
        val channelGroups by channelGroupsModel.groups.observeAsState(emptyList())

        var currentSelectedGroup by remember { mutableIntStateOf(selectedFilterGroup) }
        var currentSortOrder by remember { mutableIntStateOf(selectedSortOrder) }
        var currentHideWatched by remember { mutableStateOf(hideWatched) }
        var currentShowUpcoming by remember { mutableStateOf(showUpcoming) }

        var processedFeed by remember { mutableStateOf<List<SubscriptionItemState>?>(null) }

        LaunchedEffect(videoFeed, currentSelectedGroup, currentSortOrder, currentHideWatched, currentShowUpcoming) {
            processedFeed = processFeed(videoFeed, currentSelectedGroup, currentSortOrder, currentHideWatched, currentShowUpcoming)
        }

        val groupNames = remember(channelGroups) {
            listOf(getString(R.string.all)) + channelGroups.map { it.name }
        }

        SubscriptionsScreen(
            state = SubscriptionsScreenState(
                videos = processedFeed,
                channelGroups = groupNames,
                selectedGroupIndex = currentSelectedGroup,
                isLoading = videoFeed == null,
                feedProgress = feedProgress,
                isEmpty = videoFeed?.isEmpty() == true
            ),
            onVideoClick = { videoId ->
                NavigationHelper.navigateVideo(requireContext(), videoId)
            },
            onRefresh = {
                viewModel.fetchSubscriptions(requireContext())
                viewModel.fetchFeed(requireContext(), forceRefresh = true)
            },
            onSortFilterClick = {
                showSortFilterDialog { sort, hide, upcoming ->
                    currentSortOrder = sort
                    currentHideWatched = hide
                    currentShowUpcoming = upcoming
                }
            },
            onToggleSubsClick = {
                SubscriptionsBottomSheet().show(childFragmentManager, null)
            },
            onEditGroupsClick = {
                ChannelGroupsSheet().show(childFragmentManager, null)
            },
            onGroupClick = { index ->
                currentSelectedGroup = index
                selectedFilterGroup = index
            },
            onGroupLongClick = { index ->
                lifecycleScope.launch {
                    playByGroup(index, videoFeed, currentSortOrder, currentHideWatched, currentShowUpcoming)
                }
            }
        )
    }

    private suspend fun processFeed(
        videoFeed: List<StreamItem>?,
        groupIndex: Int,
        sortOrder: Int,
        hide: Boolean,
        upcoming: Boolean
    ): List<SubscriptionItemState>? {
        if (videoFeed == null) return null

        val feed = videoFeed
            .filterByGroup(groupIndex)
            .let {
                DatabaseHelper.filterByStreamTypeAndWatchPosition(it, hide, upcoming)
            }

        val sortedFeed = feed
            .sortedBySelectedOrder(sortOrder)
            .toMutableList()

        val result = sortedFeed.map { SubscriptionItemState.Video(it.toVideoCardState()) }.toMutableList<SubscriptionItemState>()

        // add an "all caught up item"
        if (sortOrder == 0) {
            val lastCheckedFeedTime = PreferenceHelper.getLastCheckedFeedTime(seenByUser = true)
            val caughtUpIndex = feed.indexOfFirst { it.uploaded <= lastCheckedFeedTime && !it.isUpcoming }
            if (caughtUpIndex > 0 && !feed[caughtUpIndex - 1].isUpcoming) {
                result.add(caughtUpIndex, SubscriptionItemState.AllCaughtUp)
            }
        }

        return result
    }

    private fun List<StreamItem>.filterByGroup(groupIndex: Int): List<StreamItem> {
        if (groupIndex == 0) return this

        val group = channelGroupsModel.groups.value?.getOrNull(groupIndex - 1)
        return filter {
            val channelId = it.uploaderUrl.orEmpty().toID()
            group?.channels?.contains(channelId) != false
        }
    }

    private fun List<StreamItem>.sortedBySelectedOrder(sortOrder: Int) = when (sortOrder) {
        0 -> this
        1 -> this.reversed()
        2 -> this.sortedBy { it.views }.reversed()
        3 -> this.sortedBy { it.views }
        4 -> this.sortedBy { it.uploaderName }
        5 -> this.sortedBy { it.uploaderName }.reversed()
        else -> this
    }

    private fun showSortFilterDialog(onResult: (Int, Boolean, Boolean) -> Unit) {
        childFragmentManager.setFragmentResultListener(FILTER_SORT_REQUEST_KEY, this) { _, resultBundle ->
            val sort = resultBundle.getInt(IntentData.sortOptions)
            val hide = resultBundle.getBoolean(IntentData.hideWatched)
            val upcoming = resultBundle.getBoolean(IntentData.showUpcoming)
            
            selectedSortOrder = sort
            hideWatched = hide
            showUpcoming = upcoming
            onResult(sort, hide, upcoming)
        }

        FilterSortBottomSheet().apply {
            arguments = bundleOf(
                IntentData.sortOptions to fetchSortOptions(),
                IntentData.hideWatched to hideWatched,
                IntentData.showUpcoming to showUpcoming,
            )
        }.show(childFragmentManager, null)
    }

    private fun fetchSortOptions(): List<SelectableOption> {
        return resources.getStringArray(R.array.sortOptions)
            .mapIndexed { index, option ->
                SelectableOption(isSelected = index == selectedSortOrder, name = option)
            }
    }

    private suspend fun playByGroup(
        groupIndex: Int,
        videoFeed: List<StreamItem>?,
        sortOrder: Int,
        hide: Boolean,
        upcoming: Boolean
    ) {
        if (videoFeed == null) return

        val streams = videoFeed
            .filterByGroup(groupIndex)
            .let {
                DatabaseHelper.filterByStreamTypeAndWatchPosition(it, hide, upcoming)
            }
            .sortedBySelectedOrder(sortOrder)

        if (streams.isEmpty()) return

        PlayingQueue.setStreams(streams)

        NavigationHelper.navigateVideo(
            requireContext(),
            videoId = streams.first().url,
            keepQueue = true
        )
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (viewModel.videoFeed.value == null) {
            viewModel.fetchFeed(requireContext(), forceRefresh = false)
        }
        if (viewModel.subscriptions.value == null) {
            viewModel.fetchSubscriptions(requireContext())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val groups = DatabaseHolder.Database.subscriptionGroupsDao().getAll()
                .sortedBy { it.index }
            channelGroupsModel.groups.postValue(groups)
        }
    }

    fun removeItem(videoId: String) {
        viewModel.removeItem(videoId)
    }
}


