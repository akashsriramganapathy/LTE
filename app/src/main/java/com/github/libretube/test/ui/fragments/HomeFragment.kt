package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.TrendingCategory
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.constants.PreferenceKeys.HOME_TAB_CONTENT
import com.github.libretube.test.db.obj.PlaylistBookmark
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.LocaleHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.components.PlaylistCardState
import com.github.libretube.test.ui.components.VideoCardState
import android.text.format.DateUtils
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.models.HomeViewModel
import com.github.libretube.test.ui.models.SubscriptionsViewModel
import com.github.libretube.test.ui.models.TrendsViewModel
import com.github.libretube.test.ui.screens.HomeScreen
import com.github.libretube.test.ui.screens.HomeScreenState
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class HomeFragment : Fragment() {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()
    private val trendsViewModel: TrendsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    HomeContent()
                }
            }
        }
    }

    @Composable
    private fun HomeContent() {
        val trending by homeViewModel.trending.observeAsState()
        val feed by homeViewModel.feed.observeAsState()
        val bookmarks by homeViewModel.bookmarks.observeAsState()
        val playlists by homeViewModel.playlists.observeAsState()
        val continueWatching by homeViewModel.continueWatching.observeAsState()
        val isLoading by homeViewModel.isLoading.observeAsState(true)

        val state = HomeScreenState(
            featured = feed?.map { it.toVideoCardState() },
            continueWatching = continueWatching?.map { it.toVideoCardState() },
            trending = trending?.second?.streams?.take(10)?.map { it.toVideoCardState() },
            bookmarks = bookmarks?.map { it.toPlaylistCardState() },
            playlists = playlists?.map { it.toPlaylistCardState() },
            isLoading = isLoading,
            isRefreshing = isLoading && (feed != null || trending != null),
            trendingCategory = trending?.first?.name,
            trendingRegionName = PreferenceHelper.getTrendingRegion(requireContext())
        )

        HomeScreen(
            state = state,
            onVideoClick = { videoId ->
                NavigationHelper.navigateVideo(requireContext(), videoId)
            },
            onPlaylistClick = { playlistId ->
                NavigationHelper.navigatePlaylist(requireContext(), playlistId, PlaylistType.PUBLIC)
            },
            onSectionClick = { section ->
                when (section) {
                    "featured" -> findNavController().navigate(R.id.action_homeFragment_to_subscriptionsFragment)
                    "watching" -> findNavController().navigate(R.id.action_homeFragment_to_watchHistoryFragment)
                    "trending" -> findNavController().navigate(R.id.action_homeFragment_to_trendsFragment)
                    "playlists" -> findNavController().navigate(
                        R.id.action_homeFragment_to_libraryListingFragment,
                        bundleOf("type" to LibraryListingFragment.Type.PLAYLISTS.name)
                    )
                    "bookmarks" -> findNavController().navigate(
                        R.id.action_homeFragment_to_libraryListingFragment,
                        bundleOf("type" to LibraryListingFragment.Type.BOOKMARKS.name)
                    )
                }
            },
            onTrendingCategoryClick = { showCategoryDialog() },
            onTrendingRegionClick = {
                val currentRegionPref = PreferenceHelper.getTrendingRegion(requireContext())
                val countries = LocaleHelper.getAvailableCountries()
                var selected = countries.indexOfFirst { it.code == currentRegionPref }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.region)
                    .setSingleChoiceItems(
                        countries.map { it.name }.toTypedArray(),
                        selected
                    ) { _, checked ->
                        selected = checked
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.okay) { _, _ ->
                        PreferenceHelper.putString(PreferenceKeys.REGION, countries[selected].code)
                        fetchHomeFeed()
                    }
                    .show()
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val isTrendingRegionChanged = homeViewModel.trending.value?.let {
            it.second.region != PreferenceHelper.getTrendingRegion(requireContext())
        } == true

        if (homeViewModel.loadedSuccessfully.value == false || isTrendingRegionChanged) {
            fetchHomeFeed()
        }
    }

    private fun fetchHomeFeed() {
        val defaultItems = resources.getStringArray(R.array.homeTabItemsValues)
        val visibleItems = PreferenceHelper.getStringSet(HOME_TAB_CONTENT, defaultItems.toSet())

        homeViewModel.loadHomeFeed(
            context = requireContext(),
            subscriptionsViewModel = subscriptionsViewModel,
            visibleItems = visibleItems,
            onUnusualLoadTime = {}
        )
    }

    private fun showCategoryDialog() {
        val trendingCategories = MediaServiceRepository.instance.getTrendingCategories()
        val currentTrendingCategoryPref = PreferenceHelper.getString(
            PreferenceKeys.TRENDING_CATEGORY,
            TrendingCategory.LIVE.name
        ).let { categoryName -> trendingCategories.first { it.name == categoryName } }

        val categories = trendingCategories.map { category ->
            category to getString(category.titleRes)
        }

        var selected = trendingCategories.indexOf(currentTrendingCategoryPref)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.category)
            .setSingleChoiceItems(
                categories.map { it.second }.toTypedArray(),
                selected
            ) { _, checked ->
                selected = checked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.okay) { _, _ ->
                PreferenceHelper.putString(
                    PreferenceKeys.TRENDING_CATEGORY,
                    trendingCategories[selected].name
                )
                fetchHomeFeed()
            }
            .show()
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


