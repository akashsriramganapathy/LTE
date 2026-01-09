package com.github.libretube.test.ui.extensions

import com.github.libretube.test.api.obj.Comment

fun List<Comment>.filterNonEmptyComments(): List<Comment> {
    return filter { !it.commentText.isNullOrEmpty() }
}

