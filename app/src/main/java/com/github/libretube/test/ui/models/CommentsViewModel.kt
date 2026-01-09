package com.github.libretube.test.ui.models

import android.content.Context
import androidx.core.text.parseAsHtml
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.flatMapLatest
import com.github.libretube.test.api.obj.Comment
import com.github.libretube.test.extensions.updateIfChanged
import com.github.libretube.test.helpers.ClipboardHelper
import com.github.libretube.test.ui.models.sources.CommentPagingSource

class CommentsViewModel : ViewModel() {

    private var lastOpenedCommentRepliesId: String? = null
    val videoIdLiveData = MutableLiveData<String>()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val commentsFlow = videoIdLiveData.asFlow().flatMapLatest { videoId ->
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            CommentPagingSource(videoId) {
                _commentCountLiveData.updateIfChanged(it)
            }
        }.flow
    }
        .cachedIn(viewModelScope)

    private val _commentCountLiveData = MutableLiveData<Long>()
    val commentCountLiveData: LiveData<Long> = _commentCountLiveData

    private val _currentCommentsPosition = MutableLiveData(0)
    val currentCommentsPosition: LiveData<Int> = _currentCommentsPosition

    private val _currentRepliesPosition = MutableLiveData(0)
    val currentRepliesPosition: LiveData<Int> = _currentRepliesPosition

    fun reset() {
        _currentCommentsPosition.value = 0
    }

    fun setCommentsPosition(position: Int) {
        if (position != currentCommentsPosition.value) {
            _currentCommentsPosition.value = position
        }
    }

    fun setRepliesPosition(position: Int) {
        if (position != currentRepliesPosition.value) {
            _currentRepliesPosition.value = position
        }
    }

    fun setLastOpenedCommentRepliesId(id: String) {
        if (lastOpenedCommentRepliesId != id) {
            _currentRepliesPosition.value = 0
            lastOpenedCommentRepliesId = id
        }
    }

    fun saveToClipboard(context: Context, comment: Comment) {
        ClipboardHelper.save(
            context,
            text = comment.commentText.orEmpty().parseAsHtml().toString(),
            notify = true
        )
    }
}

