package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.setFragmentResult
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.models.CommentsViewModel
import com.github.libretube.test.ui.screens.CommentsScreen
import com.github.libretube.test.ui.sheets.CommentsSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme
import kotlinx.coroutines.flow.distinctUntilChanged

class CommentsMainFragment : Fragment() {

    private val viewModel: CommentsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    CommentsMainContent()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Composable
    private fun CommentsMainContent() {
        val comments = viewModel.commentsFlow.collectAsLazyPagingItems()
        val commentCount by viewModel.commentCountLiveData.observeAsState()
        val channelAvatar = arguments?.getString(IntentData.channelAvatar)
        val commentsSheet = parentFragment as? CommentsSheet

        LaunchedEffect(commentCount) {
            commentCount?.let {
                commentsSheet?.updateFragmentInfo(
                    false,
                    getString(R.string.comments_count, it.formatShort())
                )
            }
        }

        // Handle scroll to top button visibility
        // We need to know if we are at the top or not. 
        // We can use the viewModel's position or just a local state if we had access to the list state.
        // For 1:1, let's just assume position 0 is top.
        
        CommentsScreen(
            comments = comments,
            isReplies = false,
            channelAvatar = channelAvatar,
            onCommentClick = { comment ->
                if (comment.repliesPage != null) {
                    val args = bundleOf(
                        IntentData.videoId to viewModel.videoIdLiveData.value,
                        IntentData.comment to comment,
                        IntentData.channelAvatar to channelAvatar
                    )
                    parentFragmentManager.commit {
                        viewModel.setLastOpenedCommentRepliesId(comment.commentId)
                        replace<CommentsRepliesFragment>(R.id.commentFragContainer, args = args)
                        addToBackStack(null)
                    }
                }
            },
            onCommentLongClick = { comment ->
                viewModel.saveToClipboard(requireContext(), comment)
            },
            onAuthorClick = { comment ->
                NavigationHelper.navigateChannel(requireContext(), comment.commentorUrl)
                setFragmentResult(CommentsSheet.DISMISS_SHEET_REQUEST_KEY, Bundle.EMPTY)
            },
            onLinkClick = { url ->
                setFragmentResult(
                    CommentsSheet.HANDLE_LINK_REQUEST_KEY,
                    bundleOf(IntentData.url to url),
                )
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val commentsSheet = parentFragment as? CommentsSheet
        commentsSheet?.updateFragmentInfo(false, getString(R.string.comments))
    }
}
