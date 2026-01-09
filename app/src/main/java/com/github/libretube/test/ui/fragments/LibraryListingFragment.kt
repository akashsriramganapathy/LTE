package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.models.LibraryViewModel
import com.github.libretube.test.ui.screens.LibraryListingScreen
import com.github.libretube.test.ui.sheets.BaseBottomSheet
import com.github.libretube.test.ui.sheets.PlaylistOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme

class LibraryListingFragment : Fragment() {
    private val args by navArgs<LibraryListingFragmentArgs>()
    private val viewModel: LibraryViewModel by activityViewModels()

    enum class Type { PLAYLISTS, BOOKMARKS }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val type = Type.valueOf(args.type)
        val isPlaylists = type == Type.PLAYLISTS

        return ComposeView(requireContext()).apply {
            setContent {
                LibreTubeTheme {
                    LibraryListingScreen(
                        viewModel = viewModel,
                        isPlaylists = isPlaylists,
                        onBackClick = { findNavController().popBackStack() },
                        onItemClick = { id, playlistType ->
                            NavigationHelper.navigatePlaylist(requireContext(), id, playlistType)
                        },
                        onOptionsClick = { id, name, playlistType ->
                            showOptions(id, name, playlistType)
                        },
                        onSortClick = { showSortMenu() }
                    )
                }
            }
        }
    }

    private fun showOptions(id: String, name: String, type: PlaylistType) {
        PlaylistOptionsBottomSheet().apply {
            arguments = bundleOf(
                IntentData.playlistId to id,
                IntentData.playlistName to name,
                IntentData.playlistType to type
            )
        }.show(childFragmentManager, null)
    }

    private fun showSortMenu() {
        val sortOptions = resources.getStringArray(R.array.playlistSortingOptions)
        val sortOptionValues = resources.getStringArray(R.array.playlistSortingOptionsValues)

        BaseBottomSheet().apply {
            setSimpleItems(sortOptions.toList()) { index ->
                val value = sortOptionValues[index]
                PreferenceHelper.putString(PreferenceKeys.PLAYLISTS_ORDER, value)
                viewModel.refreshData()
            }
        }.show(childFragmentManager, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refreshData()
    }
}
