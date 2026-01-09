package com.github.libretube.test.api.obj

import android.os.Parcelable
import com.github.libretube.test.db.obj.DownloadItem
import com.github.libretube.test.enums.FileType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.nio.file.Paths

@Serializable
@Parcelize
data class StreamFormat(
    val url: String,
    val format: String?,
    val height: Int? = null,
    val width: Int? = null,
    val quality: String,
    val mimeType: String?,
    val bitrate: Long,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val fps: Int? = null,
    val contentLength: Long,
    val codec: String?,
    val audioTrackId: String? = null,
    val audioTrackName: String? = null,
    val audioTrackLocale: String? = null,
    val audioTrackType: String? = null,
    val videoOnly: Boolean = false
) : Parcelable {
    fun toDownloadItem(type: FileType, videoId: String): DownloadItem {
        val extension = mimeType?.split("/")?.getOrNull(1)?.split(";")?.getOrNull(0) ?: "bin"
        val fileName = "${videoId}_${quality}_${format ?: "stream"}.${extension}"
        
        val item = DownloadItem(
            type = type,
            videoId = videoId,
            fileName = fileName,
            path = Paths.get("")
        )
        item.url = url
        item.format = format
        item.quality = quality
        item.language = audioTrackLocale
        item.downloadSize = contentLength
        return item
    }
}
