package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.libretube.test.R
import com.github.libretube.test.api.JsonHelper
import com.github.libretube.test.api.obj.ContentItem
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHelper
import com.github.libretube.test.db.obj.SearchHistoryItem
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.activities.MainActivity
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.extensions.setOnBackPressed
import com.github.libretube.test.ui.models.SearchResultViewModel
import com.github.libretube.test.ui.screens.SearchScreen
import com.github.libretube.test.ui.sheets.ChannelOptionsBottomSheet
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.TextUtils
import com.github.libretube.test.util.TextUtils.toTimeInSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SearchResultFragment : Fragment() {
    private val args by navArgs<SearchResultFragmentArgs>()
    private val viewModel by viewModels<SearchResultViewModel>()

    private val mainActivity get() = activity as MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    SearchContent()
                }
            }
        }
    }

    @Composable
    private fun SearchContent() {
        val searchResults = viewModel.searchResultsFlow.collectAsLazyPagingItems()
        val searchSuggestion by viewModel.searchSuggestion.observeAsState()
        var selectedFilter by remember { mutableStateOf("all") }

        val timeStamp = remember(args.query) {
            args.query.toHttpUrlOrNull()?.queryParameter("t")?.toTimeInSeconds() ?: 0L
        }

        SearchScreen(
            searchResults = searchResults,
            selectedFilter = selectedFilter,
            onFilterSelected = { filter ->
                selectedFilter = filter
                viewModel.setFilter(filter)
            },
            searchSuggestion = searchSuggestion,
            onSuggestionClick = { suggestion ->
                mainActivity.setQuery(suggestion, true)
            },
            onVideoClick = { item ->
                NavigationHelper.navigateVideo(requireContext(), item.url, timestamp = timeStamp)
            },
            onVideoLongClick = { item ->
                val sheet = VideoOptionsBottomSheet()
                val contentItemString = JsonHelper.json.encodeToString(item)
                val streamItem: StreamItem = JsonHelper.json.decodeFromString(contentItemString)
                sheet.arguments = bundleOf(IntentData.streamItem to streamItem)
                sheet.show(childFragmentManager, SearchResultFragment::class.java.name)
            },
            onChannelClick = { item ->
                NavigationHelper.navigateChannel(requireContext(), item.url)
            },
            onChannelLongClick = { item ->
                val channelOptionsSheet = ChannelOptionsBottomSheet()
                channelOptionsSheet.arguments = bundleOf(
                    IntentData.channelId to item.url,
                    IntentData.channelName to item.name,
                    IntentData.isSubscribed to false // This will be handled inside the sheet if possible or needs better state
                )
                channelOptionsSheet.show(childFragmentManager, SearchResultFragment::class.java.name)
            },
            onPlaylistClick = { item ->
                NavigationHelper.navigatePlaylist(requireContext(), item.url, PlaylistType.PUBLIC)
            },
            onPlaylistLongClick = { item ->
                val sheet = PlaylistOptionsBottomSheet()
                sheet.arguments = bundleOf(
                    IntentData.playlistId to item.url,
                    IntentData.playlistName to item.name.orEmpty(),
                    IntentData.playlistType to PlaylistType.PUBLIC
                )
                sheet.show(childFragmentManager, SearchResultFragment::class.java.name)
            },
            onUploaderClick = { uploaderUrl ->
                NavigationHelper.navigateChannel(requireContext(), uploaderUrl)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setQuerySilent(args.query)
        addToHistory(args.query)

        setOnBackPressed {
            findNavController().popBackStack(R.id.searchFragment, true) ||
                    findNavController().popBackStack()
        }
    }

    private fun addToHistory(query: String) {
        val searchHistoryEnabled =
            PreferenceHelper.getBoolean(PreferenceKeys.SEARCH_HISTORY_TOGGLE, true)
        if (searchHistoryEnabled && query.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                DatabaseHelper.addToSearchHistory(SearchHistoryItem(query.trim()))
            }
        }
    }
}

