package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.screens.DownloadsScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme

enum class DownloadTab {
    VIDEO,
    AUDIO,
    PLAYLIST,
    ALL
}

class DownloadsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    DownloadsScreen(
                        onNavigateToPlaylist = { playlistId ->
                            // Navigate to playlist
                        },
                        onNavigateToVideo = { videoId ->
                            NavigationHelper.navigateVideo(requireContext(), videoId)
                        }
                    )
                }
            }
        }
    }
}
