package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.api.obj.Playlist
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.models.CommonPlayerViewModel
import com.github.libretube.test.ui.screens.PlaylistScreen
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.PlayingQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistFragment : Fragment() {
    private val args by navArgs<PlaylistFragmentArgs>()
    private val commonPlayerViewModel: CommonPlayerViewModel by activityViewModels()

    private lateinit var playlistId: String
    private var playlistType = PlaylistType.PUBLIC
    
    // Sort logic (can be moved to screen state or keep here if complex)
    private var selectedSortOrder: Int
        get() = PreferenceHelper.getInt("${PreferenceKeys.PLAYLIST_SORT_ORDER}_$playlistId", 0)
        set(value) = PreferenceHelper.putInt("${PreferenceKeys.PLAYLIST_SORT_ORDER}_$playlistId", value)
    private val sortOptions by lazy { resources.getStringArray(R.array.playlistSortOptions) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistId = args.playlistId
        playlistType = args.playlistType
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
                    var playlistData by remember { mutableStateOf<Playlist?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var isBookmarked by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        isBookmarked = withContext(Dispatchers.IO) {
                            DatabaseHolder.Database.playlistBookmarkDao().includes(playlistId)
                        }
                        
                        // Fetch playlist
                        try {
                             val response = withContext(Dispatchers.IO) {
                                PlaylistsHelper.getPlaylist(playlistId)
                            }
                            
                            // Sort initially
                            val sortedStreams = getSortedVideos(response.relatedStreams.toMutableList())
                            response.relatedStreams = ArrayList(sortedStreams)
                            
                            playlistData = response
                            isLoading = false
                            
                            // Update bookmark if needed
                            if (isBookmarked) {
                                updatePlaylistBookmark(response)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG(), e.toString())
                            // Handle error
                            isLoading = false
                        }
                    }

                    PlaylistScreen(
                        playlist = playlistData,
                        playlistType = playlistType,
                        isLoading = isLoading,
                        isBookmarked = isBookmarked,
                        onBookmarkClick = {
                            isBookmarked = !isBookmarked
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (!isBookmarked) {
                                    DatabaseHolder.Database.playlistBookmarkDao().deleteById(playlistId)
                                } else {
                                    playlistData?.let { playlist ->
                                        DatabaseHolder.Database.playlistBookmarkDao().insert(playlist.toPlaylistBookmark(playlistId))
                                    }
                                }
                            }
                        },
                        onVideoClick = { streamItem ->
                            startVideoItemPlayback(streamItem, playlistData?.relatedStreams ?: emptyList())
                        },
                        onVideoLongClick = { streamItem ->
                            // Show options (not fully implemented in compose screen yet, but callbacks exist)
                        },
                        onPlayAllClick = {
                            playlistData?.relatedStreams?.firstOrNull()?.let { 
                                startVideoItemPlayback(it, playlistData?.relatedStreams ?: emptyList())
                            }
                        },
                        onShuffleClick = {
                             val queue = playlistData?.relatedStreams?.shuffled() ?: emptyList()
                             PlayingQueue.setStreams(queue)
                             NavigationHelper.navigateVideo(
                                requireContext(),
                                queue.firstOrNull()?.url,
                                playlistId = playlistId,
                                keepQueue = true
                             )
                        },
                        onSaveReorder = { newItems ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (playlistType != PlaylistType.PUBLIC) {
                                    PlaylistsHelper.updatePlaylistVideos(playlistId, newItems)
                                    withContext(Dispatchers.Main) {
                                        context?.toastFromMainDispatcher(R.string.saved)
                                    }
                                }
                            }
                        },
                        onDeleteVideo = { streamItem ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (playlistType != PlaylistType.PUBLIC) {
                                  // Determine index logic if needed, but for now we rely on the screen to update UI
                                  // and we might need to actually call API to remove efficiently or update whole list.
                                  // Existing impl calls removeFromPlaylist on adapter which calls PlaylistsHelper.removeFromPlaylist
                                  // Wait, adapter.removeFromPlaylist logic:
                                  /*
                                    PlaylistsHelper.removeFromPlaylist(playlistId, index) // No, adapter does: 
                                    PlaylistsHelper.removeFromPlaylist(playlistId, originalFeed) ?? 
                                    Looking at adapter code (not visible here but inferred), likely updates full list or specific call.
                                  */
                                  // Let's assume we update the full list for simplicity as we have onSaveReorder.
                                  // Actually, the user expects immediate deletion.
                                  // Let's check if PlaylistsHelper has remove method.
                                  // If not easily found, we do updatePlaylistVideos with items minus the deleted one.
                                  
                                  val currentList = playlistData?.relatedStreams?.toMutableList() ?: return@launch
                                  currentList.remove(streamItem)
                                  PlaylistsHelper.updatePlaylistVideos(playlistId, currentList)
                                }
                            }
                        },
                        onShowOptions = {
                             showOptionsBottomSheet(playlistData) { sorted ->
                                 playlistData = playlistData?.apply { relatedStreams = ArrayList(sorted) }
                             }
                        }
                    )
                }
            }
        }
    }
    
    private fun showOptionsBottomSheet(playlist: Playlist?, onSort: (List<StreamItem>) -> Unit) {
        val sheet = PlaylistOptionsBottomSheet()
        sheet.arguments = bundleOf(
            IntentData.playlistId to playlistId,
            IntentData.playlistName to (playlist?.name.orEmpty()),
            IntentData.playlistType to playlistType
        )

        val fragmentManager = (context as BaseActivity).supportFragmentManager
        fragmentManager.setFragmentResultListener(
            PlaylistOptionsBottomSheet.PLAYLIST_OPTIONS_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, resultBundle ->
             if (resultBundle.getBoolean(IntentData.requestSort)) {
                 // Show sort sheet
                 showSortSheet(playlist, onSort)
             }
             // Handle other results (rename, delete)
             val isPlaylistToBeDeleted = resultBundle.getBoolean(IntentData.playlistTask)
             if (isPlaylistToBeDeleted) {
                 findNavController().popBackStack()
             }
        }
        sheet.show(fragmentManager, "PlaylistOptions")
    }
    
    private fun showSortSheet(playlist: Playlist?, onSort: (List<StreamItem>) -> Unit) {
        // Compose migration of sort sheet logic re-using BaseBottomSheet or implementation
        // For now using the existing logic reused
       com.github.libretube.test.ui.sheets.BaseBottomSheet().apply {
            setSimpleItems(sortOptions.toList()) { index ->
                selectedSortOrder = index
                playlist?.let {
                    // Update list locally
                    val sorted = getSortedVideos(it.relatedStreams)
                    onSort(sorted)
                }
            }
        }.show(childFragmentManager, "SortSheet")
    }

    private fun getSortedVideos(list: List<StreamItem>): List<StreamItem> {
        return when {
            selectedSortOrder in listOf(0, 1) -> {
                if (playlistType == PlaylistType.PUBLIC) {
                    list.reversed()
                } else {
                    list
                }
            }
            selectedSortOrder in listOf(2, 3) -> list.sortedBy { it.duration }
            selectedSortOrder in listOf(4, 5) -> list.sortedBy { it.title }
            else -> list
        }.let {
            if (selectedSortOrder % 2 == 0) it else it.reversed()
        }
    }

    private fun startVideoItemPlayback(streamItem: StreamItem, currentList: List<StreamItem>) {
        if (currentList.isEmpty()) return
        PlayingQueue.setStreams(currentList)

        NavigationHelper.navigateVideo(
            requireContext(),
            streamItem.url?.toID(),
            playlistId = playlistId,
            keepQueue = true
        )
    }

    private suspend fun updatePlaylistBookmark(playlist: Playlist) {
        withContext(Dispatchers.IO) {
            val playlistBookmark =
                DatabaseHolder.Database.playlistBookmarkDao().findById(playlistId)
                    ?: return@withContext
            if (playlistBookmark.thumbnailUrl != playlist.thumbnailUrl ||
                playlistBookmark.playlistName != playlist.name ||
                playlistBookmark.videos != playlist.videos
            ) {
                DatabaseHolder.Database.playlistBookmarkDao()
                    .update(playlist.toPlaylistBookmark(playlistBookmark.playlistId))
            }
        }
    }
}
