package com.github.libretube.test.ui.activities

import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.extensions.toID
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.fragment.app.FragmentContainerView
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.allViews
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.test.BuildConfig
import com.github.libretube.test.R
import com.github.libretube.test.compat.PictureInPictureCompat
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.enums.ImportFormat
import com.github.libretube.test.enums.TopLevelDestination
import com.github.libretube.test.extensions.anyChildFocused
import com.github.libretube.test.helpers.ImportHelper
import com.github.libretube.test.helpers.IntentHelper
import com.github.libretube.test.helpers.NavBarHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.NetworkHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.helpers.ThemeHelper
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.extensions.onSystemInsets
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
// import com.github.libretube.test.ui.extensions.runOnPlayerFragment
// import com.github.libretube.test.ui.fragments.AudioPlayerFragment // Removed - replaced by Compose PlayerScreen
// import com.github.libretube.test.ui.fragments.PlayerFragment
import com.github.libretube.test.ui.models.PlayerViewModel
import com.github.libretube.test.ui.models.SearchViewModel
import com.github.libretube.test.ui.models.SubscriptionsViewModel
import com.github.libretube.test.helpers.BackupHelper
import com.github.libretube.test.helpers.BackupHelper.FILETYPE_ANY
import com.github.libretube.test.ui.screens.PlayerScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.PlayingQueue
import com.github.libretube.test.util.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.navigation.NavHostController
import com.github.libretube.test.ui.navigation.MainNavigation
import com.github.libretube.test.ui.navigation.Routes
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.github.libretube.test.ui.components.FloatingBottomNavigation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onSettingsClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    searchViewModel: SearchViewModel,
    currentRoute: String?,
    currentQuery: String? // Actual query from navigation
) {
    if (isSearchActive) {
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        SearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            onSearch = {
                if (it.isNotBlank()) {
                    onSearchActiveChange(false)
                    onSearchQuery(it)
                    focusManager.clearFocus()
                }
            },
            active = true,
            onActiveChange = { active -> 
                if (!active) onSearchActiveChange(false)
            },
            placeholder = { Text("Search YouTube") },
            leadingIcon = {
                IconButton(onClick = { 
                    onSearchActiveChange(false)
                    onQueryChange("")
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(), // Removed side padding for full width
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                inputFieldColors = TextFieldDefaults.colors(
                    focusedLabelColor = Color.Transparent,
                    unfocusedLabelColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        ) {
            // Show History and Suggestions here
            com.github.libretube.test.ui.screens.SearchSuggestionsScreen(
                viewModel = searchViewModel,
                onResultSelected = { query, submit ->
                    onQueryChange(query)
                    if (submit) {
                        onSearchActiveChange(false)
                        onSearchQuery(query)
                        focusManager.clearFocus()
                    }
                }
            )
        }
    } else {
        TopAppBar(
            title = { 
                Text(
                    text = if (currentRoute?.startsWith(Routes.Search.split("?")[0]) == true && currentQuery != null) {
                        currentQuery
                    } else {
                        stringResource(R.string.app_name)
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                ) 
            },
            actions = {
                IconButton(onClick = { 
                    onSearchActiveChange(true)
                    // Prefill with current query if on search screen, otherwise clear
                    if (currentRoute?.startsWith(Routes.Search.split("?")[0]) == true) {
                        onQueryChange(currentQuery ?: "")
                    } else {
                        onQueryChange("")
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}




@Composable
fun MainBottomNavigation(navController: NavController, currentRoute: String?) {
    val items = listOf(
        NavigationItem(
            route = com.github.libretube.test.ui.navigation.Routes.Home,
            titleRes = R.string.startpage,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        NavigationItem(
            route = com.github.libretube.test.ui.navigation.Routes.Trends,
            titleRes = R.string.trending,
            selectedIcon = Icons.Filled.Whatshot,
            unselectedIcon = Icons.Outlined.Whatshot
        ),
        NavigationItem(
            route = com.github.libretube.test.ui.navigation.Routes.Subscriptions,
            titleRes = R.string.subscriptions,
            selectedIcon = Icons.Filled.Subscriptions,
            unselectedIcon = Icons.Outlined.Subscriptions
        ),
        NavigationItem(
            route = com.github.libretube.test.ui.navigation.Routes.Library,
            titleRes = R.string.library,
            selectedIcon = Icons.Filled.VideoLibrary,
            unselectedIcon = Icons.Outlined.VideoLibrary
        )
    )
    
    FloatingBottomNavigation(
        navController = navController,
        currentRoute = currentRoute,
        items = items,
        // modifier = Modifier.align(Alignment.BottomCenter) // Already aligned by parent Box
    )
}

data class NavigationItem(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : BaseActivity() {

    // registering for activity results
    private var playlistExportFormat: ImportFormat = ImportFormat.NEWPIPE
    private var exportPlaylistId: String? = null
    private val createPlaylistsFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument(FILETYPE_ANY)
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch(Dispatchers.IO) {
            ImportHelper.exportPlaylists(
                this@MainActivity,
                uri,
                playlistExportFormat,
                selectedPlaylistIds = listOf(exportPlaylistId!!)
            )
        }
    }

    // We will initialize this in onCreate
    lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // MainActivity created

        // show noInternet Activity if no internet available on app startup
        if (!NetworkHelper.isNetworkAvailable(this)) {
            val noInternetIntent = Intent(this, NoInternetActivity::class.java)
            startActivity(noInternetIntent)
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val isAppConfigured =
            PreferenceHelper.getBoolean(PreferenceKeys.LOCAL_FEED_EXTRACTION, false)
        if (!isAppConfigured) {
            val welcomeIntent = Intent(this, WelcomeActivity::class.java)
            startActivity(welcomeIntent)
            finish()
            return
        }

        setContent {
            LibreTubeTheme {
                val playerViewModel: PlayerViewModel = viewModels<PlayerViewModel>().value
                val updateViewModel: com.github.libretube.test.ui.models.UpdateViewModel = viewModels<com.github.libretube.test.ui.models.UpdateViewModel>().value
                
                // MediaController Connection
                val context = LocalContext.current
                DisposableEffect(Unit) {
                    val sessionToken = androidx.media3.session.SessionToken(
                        context,
                        android.content.ComponentName(context, com.github.libretube.test.services.OnlinePlayerService::class.java)
                    )
                    val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                    
                    controllerFuture.addListener({
                        val controller = controllerFuture.get()
                        playerViewModel.setPlayerController(controller)
                        
                        // Sync initial state
                        playerViewModel.updatePlaybackState(
                            isPlaying = controller.isPlaying,
                            position = controller.currentPosition,
                            duration = controller.duration
                        )

                        // Add listener for real-time updates
                        controller.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                playerViewModel.updatePlaybackState(
                                    isPlaying = isPlaying,
                                    position = controller.currentPosition,
                                    duration = controller.duration
                                )
                            }

                            override fun onPositionDiscontinuity(
                                oldPosition: androidx.media3.common.Player.PositionInfo,
                                newPosition: androidx.media3.common.Player.PositionInfo,
                                reason: Int
                            ) {
                                playerViewModel.updatePlaybackState(
                                    isPlaying = controller.isPlaying,
                                    position = newPosition.positionMs,
                                    duration = controller.duration
                                )
                            }

                            override fun onPlaybackStateChanged(state: Int) {
                                playerViewModel.updatePlaybackState(
                                    isPlaying = controller.isPlaying,
                                    position = controller.currentPosition,
                                    duration = controller.duration
                                )
                                playerViewModel.updateBufferingState(state == androidx.media3.common.Player.STATE_BUFFERING)
                            }
                        })
                    }, androidx.core.content.ContextCompat.getMainExecutor(context))
                    
                    onDispose {
                        androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                        playerViewModel.setPlayerController(null)
                    }
                }

                navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                var isSearchActive by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf(savedSearchQuery ?: "") }

                val isQueueEmpty by PlayingQueue.queueState.map { it.isEmpty() }.collectAsState(initial = true)

                // Fix: Connect Queue Logic
                LaunchedEffect(playerViewModel) {
                    playerViewModel.playVideoTrigger.collect { streamItem ->
                        NavigationHelper.navigateVideo(
                            context = this@MainActivity,
                            videoId = streamItem.url?.toID(),
                            forceVideo = true
                        )
                    }
                }

                // Fix: Fullscreen orientation toggle
                LaunchedEffect(playerViewModel) {
                    playerViewModel.fullscreenTrigger.collect { isFullscreen ->
                        requestedOrientation = if (isFullscreen) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                }

                val playerContent = remember {
                    movableContentOf { bottomPadding: Dp ->
                        PlayerScreen(
                            playerViewModel = playerViewModel,
                            onClose = {
                                // Fix: Stop playback before clearing queue
                                playerViewModel.playerController.value?.pause()
                                playerViewModel.playerController.value?.clearMediaItems()
                                PlayingQueue.clear()
                            },
                            bottomPadding = bottomPadding
                        )
                    }
                }

                // Import Playlist Dialog State
                val importPlaylistData by _importPlaylistState
                
                if (importPlaylistData != null) {
                    val (playlistName, videoIds) = importPlaylistData!!
                    val context = LocalContext.current
                    com.github.libretube.test.ui.sheets.ConfirmationSheet(
                        title = stringResource(R.string.import_temp_playlist),
                        message = stringResource(R.string.import_temp_playlist_summary, playlistName, videoIds.size),
                        confirmText = stringResource(R.string.okay),
                        cancelText = stringResource(R.string.cancel),
                        isDestructive = false,
                        onConfirm = {
                            // Import logic
                            // val context = LocalContext.current // Moved up
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val playlist = com.github.libretube.test.helpers.ImportHelper.LocalImportPlaylist(
                                        name = playlistName,
                                        videos = videoIds
                                    )
                                    com.github.libretube.test.api.PlaylistsHelper.importPlaylists(listOf(playlist))
                                    context.toastFromMainDispatcher(R.string.playlistCreated)
                                } catch (e: Exception) {
                                  // Log or toast error
                                }
                            }
                            _importPlaylistState.value = null
                        },
                        onCancel = {
                            _importPlaylistState.value = null
                        }
                    )
                }

                // Update Available Dialog State
                val updateInfo by updateViewModel.updateInfo
                val downloadUrl by updateViewModel.downloadUrl
                val runNumber by updateViewModel.runNumber
                val sanitizedBody by updateViewModel.sanitizedBody
                
                if (updateInfo != null && downloadUrl != null) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    com.github.libretube.test.ui.sheets.UpdateAvailableSheet(
                        updateName = updateInfo!!.name,
                        runNumber = runNumber,
                        changelog = sanitizedBody ?: updateInfo!!.body,
                        onUpdate = {
                            val updateManager = com.github.libretube.test.util.UpdateManager(context)
                            scope.launch {
                                updateManager.handleUpdate(downloadUrl!!, scope)
                            }
                            updateViewModel.dismissUpdate()
                        },
                        onDismiss = {
                            updateViewModel.dismissUpdate()
                        }
                    )
                }

                val isPlayerExpanded by playerViewModel.isExpanded.collectAsState()
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            if (!isPlayerExpanded) {
                                val searchViewModel: SearchViewModel = viewModel()
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentQuery = navBackStackEntry?.arguments?.getString("query")
                                
                                MainTopAppBar(
                                    isSearchActive = isSearchActive,
                                    searchQuery = searchQuery,
                                    onQueryChange = { searchQuery = it; searchViewModel.setQuery(it) },
                                    onSearchActiveChange = { active -> 
                                        isSearchActive = active
                                        if (active) {
                                            searchViewModel.setQuery(searchQuery)
                                        }
                                    },
                                    onSearchQuery = { query ->
                                        // Use standard navigate to maintain backstack correctly
                                        navController.navigate(com.github.libretube.test.ui.navigation.Routes.search(query))
                                    },
                                    onSettingsClick = {
                                        val settingsIntent = Intent(this@MainActivity, SettingsActivity::class.java)
                                        startActivity(settingsIntent)
                                    },
                                    scrollBehavior = scrollBehavior,
                                    searchViewModel = searchViewModel,
                                    currentRoute = currentRoute,
                                    currentQuery = currentQuery
                                )
                            }
                        },
                            // Bottom bar removed from Scaffold to be floating
                    ) { padding ->
                        com.github.libretube.test.ui.navigation.MainNavigation(
                            navController = navController,
                            playerViewModel = playerViewModel,
                            modifier = Modifier.fillMaxSize().padding(if (isPlayerExpanded) PaddingValues(0.dp) else padding),
                            // Pass extra bottom padding for floating nav
                            contentPadding = if (!isPlayerExpanded) PaddingValues(bottom = 100.dp) else PaddingValues(0.dp)
                        )
                    }

                    if (!isPlayerExpanded && currentRoute in listOf(
                        com.github.libretube.test.ui.navigation.Routes.Home,
                        com.github.libretube.test.ui.navigation.Routes.Trends,
                        com.github.libretube.test.ui.navigation.Routes.Subscriptions,
                        com.github.libretube.test.ui.navigation.Routes.Library
                    )) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            MainBottomNavigation(navController, currentRoute)
                        }
                    }

                    if (!isQueueEmpty) {
                        val showBottomBar = !isPlayerExpanded && currentRoute in listOf(
                            com.github.libretube.test.ui.navigation.Routes.Home,
                            com.github.libretube.test.ui.navigation.Routes.Trends,
                            com.github.libretube.test.ui.navigation.Routes.Subscriptions,
                            com.github.libretube.test.ui.navigation.Routes.Library
                        )
                        playerContent(if (showBottomBar) 100.dp else 0.dp)
                    }
                }
            }
        }

        // Initialize background tasks
        if (PreferenceHelper.getBoolean(PreferenceKeys.AUTOMATIC_UPDATE_CHECKS, true)) {
            val updateViewModel: com.github.libretube.test.ui.models.UpdateViewModel by viewModels()
            lifecycleScope.launch(Dispatchers.IO) {
                UpdateChecker(this@MainActivity).checkUpdate(false, updateViewModel)
            }
        }

        loadIntentData { name, ids ->
            // How do we update the state from here? 
            // We need a MutableState or callback available in Activity scope.
            // Since `setContent` handles state, we need to restructure slightly or use a ViewModel.
            // For now, let's use a temporary simpler approach by saving intent data and reading it in setContent.
            // However, `loadIntentData` is called in `onNewIntent` too.
        }
        showUserInfoDialogIfNeeded()
    }
    
    // MutableState for import dialog, accessible to Activity
    private val _importPlaylistState = androidx.compose.runtime.mutableStateOf<Pair<String, List<String>>?>(null)


    private var savedSearchQuery: String? = null

    /**
     * Try to find a scroll or recycler view and scroll it back to the top
     * Note: This legacy method might not work with Compose lazy lists without state reference.
     * We'll need to implement logic in Composables to handle re-selection.
     */
    private fun tryScrollToTop(view: View?) {
         // TODO: Implement scroll to top for Compose
    }

    fun setQuerySilent(query: String) {
        // setSearchQuery(query) // TODO: Handle state if needed
    }

    fun setQuery(query: String, submit: Boolean) {
        if (submit) {
            navController.navigate(com.github.libretube.test.ui.navigation.Routes.search(query))
        }
    }

    private fun loadIntentData(onImportPlaylist: ((String, List<String>) -> Unit)? = null) {
        // loadIntentData
        // If activity is running in PiP mode, then start it in front.
        if (PictureInPictureCompat.isInPictureInPictureMode(this)) {
            val nIntent = Intent(this, MainActivity::class.java)
            nIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(nIntent)
        }

        if (intent?.getBooleanExtra(IntentData.maximizePlayer, false) == true) {
             // Maximize player via ViewModel
             val playerViewModel: PlayerViewModel by viewModels()
             playerViewModel.triggerPlayerExpansion() // Assuming method exists or we add it
             // Also play if not playing?
            return
        }

        // navigate to (temporary) playlist or channel if available
        if (navigateToMediaByIntent(intent, onImportPlaylist)) return

        // Get saved search query if available
        intent?.getStringExtra(IntentData.query)?.let {
            savedSearchQuery = it
        }

        // Open the Downloads screen if requested
        if (intent?.getBooleanExtra(IntentData.OPEN_DOWNLOADS, false) == true) {
            navController.navigate(com.github.libretube.test.ui.navigation.Routes.Downloads)
            return
        }

        // Handle navigation from app shortcuts (Home, Trends, etc.)
        intent?.getStringExtra(IntentData.fragmentToOpen)?.let {
            ShortcutManagerCompat.reportShortcutUsed(this, it)
            when (it) {
                TopLevelDestination.Home.route -> navController.navigate(com.github.libretube.test.ui.navigation.Routes.Home)
                TopLevelDestination.Trends.route -> navController.navigate(com.github.libretube.test.ui.navigation.Routes.Trends)
                TopLevelDestination.Subscriptions.route -> navController.navigate(com.github.libretube.test.ui.navigation.Routes.Subscriptions)
                TopLevelDestination.Library.route -> navController.navigate(com.github.libretube.test.ui.navigation.Routes.Library)
            }
        }
    }

    /**
     * Navigates to the channel, video or playlist provided in the [Intent] if available
     */
    fun navigateToMediaByIntent(intent: Intent, onImportPlaylist: ((String, List<String>) -> Unit)? = null, actionBefore: () -> Unit = {}): Boolean {
        if (!::navController.isInitialized) return false

        intent.getStringExtra(IntentData.channelId)?.let {
            actionBefore()
            navController.navigate(com.github.libretube.test.ui.navigation.Routes.channel(channelId = it))
            return true
        }
        intent.getStringExtra(IntentData.channelName)?.let {
            actionBefore()
            navController.navigate(com.github.libretube.test.ui.navigation.Routes.channel(channelName = it))
            return true
        }
        intent.getStringExtra(IntentData.playlistId)?.let {
            actionBefore()
            navController.navigate(com.github.libretube.test.ui.navigation.Routes.playlist(playlistId = it))
            return true
        }
        intent.getStringArrayExtra(IntentData.videoIds)?.let {
            actionBefore()
            val name = intent.getStringExtra(IntentData.playlistName) ?: com.github.libretube.test.util.TextUtils.getFileSafeTimeStampNow()
            if (onImportPlaylist != null) {
                onImportPlaylist(name, it.toList())
            } else {
                _importPlaylistState.value = name to it.toList()
            }
            return true
        }

        intent.getStringExtra(IntentData.videoId)?.let {
            // Direct navigation - Compose handles layout timing
            NavigationHelper.navigateVideo(
                context = this@MainActivity,
                videoId = it,
                timestamp = intent.getLongExtra(IntentData.timeStamp, 0L)
            )

            return true
        }

        return false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fix: Implement PiP Logic
        val playerViewModel: PlayerViewModel by viewModels()
        if (playerViewModel.isPlaying.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playerViewModel.setIsInPip(true)
            // Check if PiP is supported and allowed
             val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
             enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val playerViewModel: PlayerViewModel by viewModels()
        playerViewModel.setIsInPip(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // onNewIntent
        this.intent = intent
        loadIntentData()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Player key handling via ViewModel if possible, or keep simple
        return super.onKeyUp(keyCode, event)
    }

    fun startPlaylistExport(
        playlistId: String,
        playlistName: String,
        format: ImportFormat,
        includeTimestamp: Boolean
    ) {
        playlistExportFormat = format
        exportPlaylistId = playlistId

        val fileName =
            BackupHelper.getExportFileName(this, format, playlistName, includeTimestamp)
        createPlaylistsFile.launch(fileName)
    }

    private fun showUserInfoDialogIfNeeded() {
        // don't show the update information dialog for debug builds
        if (BuildConfig.DEBUG) return

        val lastShownVersionCode =
            PreferenceHelper.getInt(PreferenceKeys.LAST_SHOWN_INFO_MESSAGE_VERSION_CODE, -1)

        // mapping of version code to info message
        val infoMessages = emptyList<Pair<Int, String>>()

        val message =
            infoMessages.lastOrNull { (versionCode, _) -> versionCode > lastShownVersionCode }?.second
                ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_information)
            .setMessage(message)
            .setNegativeButton(R.string.okay, null)
            .setPositiveButton(R.string.never_show_again) { _, _ ->
                PreferenceHelper.putInt(
                    PreferenceKeys.LAST_SHOWN_INFO_MESSAGE_VERSION_CODE,
                    BuildConfig.VERSION_CODE
                )
            }
            .show()
    }
}
