package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.models.TrendsViewModel
import com.github.libretube.test.ui.screens.TrendsScreen
import com.github.libretube.test.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme

class TrendsFragment : Fragment() {
    private val viewModel: TrendsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    val categories = MediaServiceRepository.instance.getTrendingCategories()
                    
                    TrendsScreen(
                        categories = categories,
                        viewModel = viewModel,
                        onVideoClick = { streamItem ->
                            NavigationHelper.navigateVideo(requireContext(), streamItem.url)
                        },
                        onVideoLongClick = { streamItem ->
                            val sheet = VideoOptionsBottomSheet()
                            sheet.arguments = bundleOf(IntentData.streamItem to streamItem)
                            sheet.show(childFragmentManager, VideoOptionsBottomSheet::class.java.name)
                        }
                    )
                }
            }
        }
    }
}
