package com.github.libretube.test.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.Comment
import com.github.libretube.test.util.HtmlParser
import com.github.libretube.test.util.LinkHandler
import com.github.libretube.test.util.TextUtils
import com.google.android.material.textview.MaterialTextView
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.text.parseAsHtml
import com.github.libretube.test.extensions.formatShort

import com.github.libretube.test.helpers.ThemeHelper

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommentsScreen(
    comments: LazyPagingItems<Comment>,
    isReplies: Boolean,
    channelAvatar: String?,
    onCommentClick: (Comment) -> Unit,
    onCommentLongClick: (Comment) -> Unit,
    onAuthorClick: (Comment) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // ... (rest)
        if (comments.loadState.refresh is LoadState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (comments.loadState.refresh is LoadState.Error) {
            Text(
                text = stringResource(R.string.no_comments_available),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (comments.itemCount == 0 && comments.loadState.append.endOfPaginationReached) {
            Text(
                text = stringResource(R.string.no_comments_available),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    count = comments.itemCount,
                    key = comments.itemKey { it.commentId }
                ) { index ->
                    val comment = comments[index] ?: return@items
                    CommentItem(
                        comment = comment,
                        isReplies = isReplies,
                        isParent = isReplies && index == 0,
                        channelAvatar = channelAvatar,
                        onClick = { onCommentClick(comment) },
                        onLongClick = { onCommentLongClick(comment) },
                        onAuthorClick = { onAuthorClick(comment) },
                        onLinkClick = onLinkClick
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: Comment,
    isReplies: Boolean,
    isParent: Boolean,
    channelAvatar: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onLinkClick: (String) -> Unit
) {
    val context = LocalContext.current
    
    // Parent comment in replies has a slightly different background
    val backgroundColor = if (isParent) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    // Indentation for replies if needed? Original code seems to just adjust margin for avatar
    val leftPadding = if (isReplies && !isParent) 48.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = leftPadding, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = comment.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(if (isParent) 32.dp else 28.dp)
                    .clip(CircleShape)
                    .clickable { onAuthorClick() },
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (comment.channelOwner) MaterialTheme.colorScheme.primary else Color.Unspecified
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = if (comment.channelOwner) 4.dp else 0.dp
                        ).background(
                            if (comment.channelOwner) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            CircleShape
                        )
                    )
                    
                    if (comment.verified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_verified),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    val timeText = comment.commentedTimeMillis?.let {
                        TextUtils.formatRelativeDate(it).toString()
                    } ?: comment.commentedTime
                    
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (comment.pinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_pinned),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val linkColor = MaterialTheme.colorScheme.primary.toArgb()
                AndroidView(
                    factory = { context ->
                        MaterialTextView(context).apply {
                            setLinkTextColor(linkColor)
                            movementMethod = LinkMovementMethodCompat.getInstance()
                            // Set custom typeface if needed
                        }
                    },
                    update = { textView ->
                        val linkHandler = LinkHandler {
                            onLinkClick(it)
                        }
                        textView.text = comment.commentText?.replace("</a>", "</a> ")
                            ?.parseAsHtml(tagHandler = HtmlParser(linkHandler))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_thumb_up),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = comment.likeCount.formatShort(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (comment.hearted) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_hearted),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Red // Heart is usually red
                        )
                    }
                    
                    if (comment.creatorReplied && !channelAvatar.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        AsyncImage(
                            model = channelAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    if (!isReplies && comment.repliesPage != null && comment.replyCount > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_comment),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.replyCount.formatShort(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
