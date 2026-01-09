package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.ui.activities.MainActivity
import com.github.libretube.test.ui.extensions.setOnBackPressed
import com.github.libretube.test.ui.models.SearchViewModel
import com.github.libretube.test.ui.screens.SearchSuggestionsScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme

class SearchSuggestionsFragment : Fragment() {
    private val viewModel: SearchViewModel by activityViewModels()
    private val mainActivity get() = activity as MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setQuery(arguments?.getString(IntentData.query))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LibreTubeTheme {
                    SearchSuggestionsScreen(
                        viewModel = viewModel,
                        onResultSelected = { query, submit ->
                            mainActivity.setQuery(query, submit)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setOnBackPressed {
            if (!mainActivity.clearSearchViewFocus()) findNavController().popBackStack()
        }
    }
}
