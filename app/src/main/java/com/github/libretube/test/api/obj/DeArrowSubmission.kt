package com.github.libretube.test.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowSubmissionBody(
    val videoID: String,
    val userID: String,
    val userAgent: String,
    val service: String = "YouTube",
    val title: DeArrowTitleSubmission? = null,
    val thumbnail: DeArrowThumbnailSubmission? = null
)

data class DeArrowSubmission(
    val videoID: String,
    val userID: String,
    val userAgent: String,
    val service: String = "YouTube",
    val title: DeArrowTitleSubmission? = null,
    val thumbnail: DeArrowThumbnailSubmission? = null
)

@Serializable
data class DeArrowTitleSubmission(
    val title: String
)

@Serializable
data class DeArrowThumbnailSubmission(
    val timestamp: Double? = null,
    val original: Boolean = false
)
