package com.github.libretube.test.obj

sealed class DownloadStatus {

    object Queued : DownloadStatus()
    object Stalled : DownloadStatus()
    object Completed : DownloadStatus()

    object Paused : DownloadStatus()

    object Stopped : DownloadStatus()

    data class Progress(
        val progress: Long,
        val downloaded: Long,
        val total: Long
    ) : DownloadStatus()

    data class Error(val message: String, val cause: Throwable? = null) : DownloadStatus()
}

