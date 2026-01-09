package com.github.libretube.test.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowContent(
    val thumbnails: List<DeArrowThumbnail>,
    val titles: List<DeArrowTitle>,
    val randomTime: Float? = null,
    val videoDuration: Float? = null
)

@Serializable
data class DeArrowTitle(
    val title: String,
    val original: Boolean,
    val votes: Int,
    val locked: Boolean,
    val UUID: String
)

@Serializable
data class DeArrowThumbnail(
    val timestamp: Double? = null,
    val original: Boolean,
    val votes: Int,
    val locked: Boolean,
    val UUID: String
)
