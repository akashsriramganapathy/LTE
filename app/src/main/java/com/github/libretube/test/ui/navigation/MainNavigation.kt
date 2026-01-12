package com.github.libretube.test.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.github.libretube.test.ui.screens.*
import com.github.libretube.test.ui.models.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.ui.screens.LibraryListingScreen
import com.github.libretube.test.ui.screens.LibraryListingType
import androidx.paging.compose.collectAsLazyPagingItems

object Routes {
    const val Home = "home"
    const val Trends = "trends"
    const val Subscriptions = "subscriptions"
    const val Library = "library"
    const val Downloads = "downloads"
    const val WatchHistory = "watch_history"
    
    const val Search = "search/{query}"
    fun search(query: String) = "search/$query"
    
    const val SearchSuggestions = "search_suggestions"
    
    const val Channel = "channel?channelId={channelId}&channelName={channelName}"
    fun channel(channelId: String? = null, channelName: String? = null): String {
        return if (channelId != null) "channel?channelId=$channelId"
        else "channel?channelName=$channelName"
    }
    
    const val Playlist = "playlist/{playlistId}?type={type}"
    fun playlist(playlistId: String, type: PlaylistType = PlaylistType.PUBLIC) = "playlist/$playlistId?type=${type.name}"
    
    const val LibraryListing = "library_listing/{type}"
    fun libraryListing(type: String) = "library_listing/$type"
    
    const val Settings = "settings"
    const val SettingsGroup = "settings/{group}"
    fun settingsGroup(group: String) = "settings/$group"
}

@Composable
fun MainNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    playerViewModel: PlayerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
        ?: throw IllegalStateException("Context must be a ComponentActivity")

    val homeViewModel: HomeViewModel = viewModel(activity)
    val trendsViewModel: TrendsViewModel = viewModel(activity)
    val subscriptionsViewModel: SubscriptionsViewModel = viewModel(activity)
    val libraryViewModel: LibraryViewModel = viewModel(activity)

    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier
    ) {
        composable(Routes.Home) {
            HomeScreen(
                navController = navController,
                homeViewModel = homeViewModel,
                subscriptionsViewModel = subscriptionsViewModel,
                contentPadding = contentPadding
            )
        }
        
        composable(Routes.Trends) {
            TrendsScreen(
                navController = navController,
                viewModel = trendsViewModel,
                contentPadding = contentPadding
            )
        }
        
        composable(Routes.Subscriptions) {
            SubscriptionsScreen(
                navController = navController,
                viewModel = subscriptionsViewModel,
                contentPadding = contentPadding
            )
        }
        
        composable(Routes.Library) {
            LibraryScreen(
                navController = navController,
                libraryViewModel = libraryViewModel,
                contentPadding = contentPadding
            )
        }
        
        composable(Routes.Downloads) {
             DownloadsScreen(
                 onNavigateToPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                 onNavigateToVideo = { id -> com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, id) }
             )
        }
        
        composable(Routes.WatchHistory) {
             val watchHistoryViewModel: WatchHistoryModel = viewModel(activity)
             WatchHistoryScreen(
                 viewModel = watchHistoryViewModel,
                 onBackClick = { navController.popBackStack() },
                 onItemClick = { item -> com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, item.videoId) },
                 onItemLongClick = {},
                 onClearHistoryClick = {},
                 onPlayAllClick = {},
                 isMiniPlayerVisible = false // Placeholder
             )
        }
        
        composable(Routes.SearchSuggestions) {
            SearchSuggestionsScreen(
                viewModel = viewModel(activity), 
                onResultSelected = { query, submit ->
                    if (submit) {
                        navController.navigate(Routes.search(query)) {
                            popUpTo(Routes.SearchSuggestions) { inclusive = true }
                        }
                    } else {
                         // TODO: Update Search Input
                    }
                }
            )
        }

        composable(
            route = Routes.Search,
            arguments = listOf(navArgument("query") { type = NavType.StringType })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            val searchViewModel: SearchScreenViewModel = viewModel(activity)
            
            // Trigger search when query changes
            LaunchedEffect(query) {
                if (query.isNotBlank()) {
                    searchViewModel.setQuery(query)
                    searchViewModel.saveToHistory(query)
                }
            }
            
            // Collect state
            val searchResults = searchViewModel.searchResults.collectAsLazyPagingItems()
            val selectedFilter by searchViewModel.selectedFilter.collectAsState()
            val searchSuggestion by searchViewModel.searchSuggestion.collectAsState()
            
            SearchScreen(
                searchResults = searchResults,
                selectedFilter = selectedFilter,
                onFilterSelected = { filter -> searchViewModel.setFilter(filter) },
                searchSuggestion = searchSuggestion,
                onSuggestionClick = { suggestion ->
                    navController.navigate(Routes.search(suggestion)) {
                        popUpTo(Routes.Search) { inclusive = true }
                    }
                },
                onVideoClick = { item -> com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, item.url) },
                onVideoLongClick = {},
                onChannelClick = { item -> com.github.libretube.test.helpers.NavigationHelper.navigateChannel(context, item.url) },
                onChannelLongClick = {},
                onPlaylistClick = { item -> com.github.libretube.test.helpers.NavigationHelper.navigatePlaylist(context, item.url, PlaylistType.PUBLIC) },
                onPlaylistLongClick = {},
                onUploaderClick = {},
                modifier = Modifier
            )
        }
        
        composable(
            route = Routes.Channel,
            arguments = listOf(
                navArgument("channelId") { 
                    type = NavType.StringType 
                    nullable = true
                },
                navArgument("channelName") { 
                    type = NavType.StringType 
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")
            val channelName = backStackEntry.arguments?.getString("channelName")
            // Needs ChannelViewModel and Route logic
            ChannelScreen(
                channelData = null,
                isLoading = true,
                onRefresh = {},
                onShowOptions = {},
                onVideoClick = {},
                onVideoLongClick = {}
            )
        }
        
        composable(
            route = Routes.Playlist,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("type") { 
                    type = NavType.StringType 
                    defaultValue = PlaylistType.PUBLIC.name 
                }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val typeStr = backStackEntry.arguments?.getString("type") ?: PlaylistType.PUBLIC.name
            val type = try { PlaylistType.valueOf(typeStr) } catch (e: Exception) { PlaylistType.PUBLIC }
            
            val playlistViewModel: PlaylistScreenModel = viewModel()
            val playlist by playlistViewModel.playlist.collectAsState()
            val isLoading by playlistViewModel.isLoading.collectAsState()
            val isBookmarked by playlistViewModel.isBookmarked.collectAsState()

            LaunchedEffect(playlistId) {
                playlistViewModel.loadPlaylist(playlistId, type)
            }

            PlaylistScreen(
                playlistId = playlistId,
                playlist = playlist,
                playlistType = type,
                isLoading = isLoading,
                isBookmarked = isBookmarked,
                onBookmarkClick = { playlistViewModel.toggleBookmark(playlistId) },
                onVideoClick = { item -> com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, item.url) },
                onVideoLongClick = { item -> /* TODO: Show options */ },
                onPlayAllClick = {
                    playlist?.relatedStreams?.let { streams ->
                        com.github.libretube.test.util.PlayingQueue.setStreams(streams)
                        com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, streams.first().url)
                    }
                },
                onShuffleClick = {
                    playlist?.relatedStreams?.let { streams ->
                        val shuffled = streams.shuffled()
                        com.github.libretube.test.util.PlayingQueue.setStreams(shuffled)
                        com.github.libretube.test.helpers.NavigationHelper.navigateVideo(context, shuffled.first().url)
                    }
                },
                onSaveReorder = { items -> playlistViewModel.saveReorder(playlistId, items) },
                onDeleteVideo = { item -> playlistViewModel.deleteVideo(playlistId, item) },
                onRenamePlaylist = { newName -> playlistViewModel.renamePlaylist(playlistId, newName) },
                onChangeDescription = { desc -> playlistViewModel.changeDescription(playlistId, desc) },
                onDeletePlaylist = { 
                    playlistViewModel.deletePlaylist(playlistId) {
                        navController.popBackStack() 
                    }
                }
            )
        }
        
        composable(
            route = Routes.LibraryListing,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type")
            val type = try { LibraryListingType.valueOf(typeStr ?: "") } catch(e:Exception) { LibraryListingType.PLAYLISTS }
            val isPlaylists = type == LibraryListingType.PLAYLISTS
            
            LibraryListingScreen(
                viewModel = libraryViewModel,
                isPlaylists = isPlaylists,
                onBackClick = { navController.popBackStack() },
                onItemClick = { id, playlistType -> 
                    navController.navigate(Routes.playlist(id, playlistType))
                }
            )
        }
        
        composable(Routes.Settings) {
             SettingsScreen(
                 onItemClick = { key ->
                     if (key == "update" || key == "view_logs") {
                         // TODO: Handle special keys
                     } else {
                         navController.navigate(Routes.settingsGroup(key))
                     }
                 },
                 versionName = com.github.libretube.test.BuildConfig.VERSION_NAME
             )
        }
        
        composable(
            route = Routes.SettingsGroup,
            arguments = listOf(navArgument("group") { type = NavType.StringType })
        ) { backStackEntry ->
             val group = backStackEntry.arguments?.getString("group") ?: ""
             // SettingsGroupScreen(group = group, navController = navController)
             // Placeholder until SettingsGroupScreen is implemented
             androidx.compose.material3.Text("Settings group: $group")
        }
    }
}
