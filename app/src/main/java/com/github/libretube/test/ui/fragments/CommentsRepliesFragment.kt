package com.github.libretube.test.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Comment
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.extensions.formatShort
import com.github.libretube.test.extensions.parcelable
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.models.CommentRepliesViewModel
import com.github.libretube.test.ui.models.CommentsViewModel
import com.github.libretube.test.ui.screens.CommentsScreen
import com.github.libretube.test.ui.sheets.CommentsSheet
import com.github.libretube.test.ui.theme.LibreTubeTheme

class CommentsRepliesFragment : Fragment() {

    private val viewModel by viewModels<CommentRepliesViewModel> { CommentRepliesViewModel.Factory }
    private val sharedModel by activityViewModels<CommentsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    CommentsRepliesContent()
                }
            }
        }
    }

    @Composable
    private fun CommentsRepliesContent() {
        val comments = viewModel.commentRepliesFlow.collectAsLazyPagingItems()
        val channelAvatar = arguments?.getString(IntentData.channelAvatar)
        val commentsSheet = parentFragment as? CommentsSheet

        CommentsScreen(
            comments = comments,
            isReplies = true,
            channelAvatar = channelAvatar,
            onCommentClick = { /* Click on reply might do nothing or show nested? */ },
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
        commentsSheet?.updateFragmentInfo(
            true,
            "${getString(R.string.replies)} (${requireArguments().parcelable<Comment>(IntentData.comment)!!.replyCount.formatShort()})"
        )
    }
}

