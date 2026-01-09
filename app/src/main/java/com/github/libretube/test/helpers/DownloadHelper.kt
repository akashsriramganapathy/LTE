package com.github.libretube.test.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.github.libretube.test.R
import com.github.libretube.test.api.PlaylistsHelper
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.db.obj.Download
import com.github.libretube.test.db.obj.DownloadItem
import com.github.libretube.test.db.obj.DownloadWithItems
import com.github.libretube.test.enums.FileType
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.parcelable.DownloadData
import com.github.libretube.test.services.DownloadService
import com.github.libretube.test.ui.dialogs.DownloadDialog
import com.github.libretube.test.ui.dialogs.DownloadPlaylistDialog
import com.github.libretube.test.ui.dialogs.ShareDialog
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.asSequence

object DownloadHelper {
    const val VIDEO_DIR = "video"
    const val AUDIO_DIR = "audio"
    const val SUBTITLE_DIR = "subtitle"
    const val THUMBNAIL_DIR = "thumbnail"
    const val PLAYLIST_THUMBNAIL_DIR = "playlist_thumbnail"
    const val DOWNLOAD_CHUNK_SIZE = 8L * 1024
    const val DEFAULT_TIMEOUT = 15 * 1000
    private const val VIDEO_MIMETYPE = "video/*"

    fun getDownloadDir(context: Context, path: String): Path {
        return (getDownloadBaseDir(context) / path).createDirectories()
    }

    fun getDownloadBaseDir(context: Context): Path {
        // Enforce Private App-Specific Storage ("data file") as requested.
        // This ensures reliability for downloads, deletions, and thumbnails.
        val privateDir = context.getExternalFilesDir(null) 
            ?: context.filesDir
        return privateDir.toPath()
    }

    private fun tryGetPathFromUri(context: Context, uri: Uri): String? {
        if ("com.android.externalstorage.documents" == uri.authority) {
            val docId = try {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            } catch (e: Exception) {
                null
            } ?: return null
            
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.size < 2) return null
            val type = split[0]
            val relativePath = split[1]

            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            } else {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                try {
                    val storageVolumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        storageManager.storageVolumes
                    } else {
                        emptyList()
                    }
                    for (volume in storageVolumes) {
                        val volumeUuid = volume.uuid
                        if (volumeUuid != null && volumeUuid == type) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                return volume.directory?.absolutePath + "/" + relativePath
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
        return null
    }

    fun getMaxConcurrentDownloads(): Int {
        return PreferenceHelper.getString(
            PreferenceKeys.MAX_CONCURRENT_DOWNLOADS,
            "1"
        ).toFloat().toInt()
    }

    fun startDownloadService(context: Context, downloadData: DownloadData? = null) {
        val intent = Intent(context, DownloadService::class.java)
            .putExtra(IntentData.downloadData, downloadData)

        ContextCompat.startForegroundService(context, intent)
    }

    fun DownloadItem.getNotificationId(): Int {
        return Int.MAX_VALUE - id
    }

    fun startDownloadDialog(context: Context, fragmentManager: FragmentManager, videoId: String) {
        val externalProviderPackageName =
            PreferenceHelper.getString(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, "")

        // Check for 'false' string which might be returned by legacy/boolean preference logic
        if (externalProviderPackageName.isBlank() || externalProviderPackageName == "false") {
            DownloadDialog().apply {
                arguments = bundleOf(IntentData.videoId to videoId)
            }.show(fragmentManager, DownloadDialog::class.java.name)
        } else {
            // "Deep" Integration for YTDLnis (most common external downloader for LTE)
            val intent = if (externalProviderPackageName.contains("ytdlnis")) {
                Intent(Intent.ACTION_SEND).apply {
                    setPackage(externalProviderPackageName)
                    putExtra(Intent.EXTRA_TEXT, "${ShareDialog.YOUTUBE_FRONTEND_URL}/watch?v=$videoId")
                    // Pass standardized naming template to facilitate auto-sync
                    putExtra("template", "%(title)s [%(id)s].%(ext)s")
                    type = "text/plain"
                }
            } else {
                Intent(Intent.ACTION_VIEW)
                    .setPackage(externalProviderPackageName)
                    .setDataAndType(
                        "${ShareDialog.YOUTUBE_FRONTEND_URL}/watch?v=$videoId".toUri(),
                        VIDEO_MIMETYPE
                    )
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // If external app fails/not installed, fallback to internal downloader
                com.github.libretube.logger.FileLogger.e("DownloadHelper", "Failed to launch external downloader: $externalProviderPackageName. Falling back to internal.", e)
                DownloadDialog().apply {
                    arguments = bundleOf(IntentData.videoId to videoId)
                }.show(fragmentManager, DownloadDialog::class.java.name)
            }
        }
    }

    fun startDownloadPlaylistDialog(
        context: Context,
        fragmentManager: FragmentManager,
        playlistId: String,
        playlistName: String,
        playlistType: PlaylistType
    ) {
        val externalProviderPackageName =
            PreferenceHelper.getString(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, "")

        if (externalProviderPackageName.isBlank()) {
            val downloadPlaylistDialog = DownloadPlaylistDialog().apply {
                arguments = bundleOf(
                    IntentData.playlistId to playlistId,
                    IntentData.playlistName to playlistName,
                    IntentData.playlistType to playlistType
                )
            }
            downloadPlaylistDialog.show(fragmentManager, null)
        } else if (playlistType == PlaylistType.PUBLIC) {
            val intent = if (externalProviderPackageName.contains("ytdlnis")) {
                Intent(Intent.ACTION_SEND).apply {
                    setPackage(externalProviderPackageName)
                    putExtra(Intent.EXTRA_TEXT, "${ShareDialog.YOUTUBE_FRONTEND_URL}/playlist?list=$playlistId")
                    putExtra("template", "%(title)s [%(id)s].%(ext)s")
                    type = "text/plain"
                }
            } else {
                Intent(Intent.ACTION_VIEW)
                    .setPackage(externalProviderPackageName)
                    .setDataAndType(
                        "${ShareDialog.YOUTUBE_FRONTEND_URL}/playlist?list=$playlistId".toUri(),
                        VIDEO_MIMETYPE
                    )
            }

            runCatching { context.startActivity(intent) }
        } else {
            // Local Playlist / Bookmarked - Send individual video URLs as a batch to external app
            CoroutineScope(Dispatchers.IO).launch {
                val playlistVideoUrls = try {
                    PlaylistsHelper.getPlaylist(playlistId)
                } catch (e: Exception) {
                    context.toastFromMainDispatcher(R.string.unknown_error)
                    return@launch
                }.relatedStreams.mapNotNull { it.url }.joinToString("\n")

                val intent = if (externalProviderPackageName.contains("ytdlnis")) {
                    Intent(Intent.ACTION_SEND).apply {
                        setPackage(externalProviderPackageName)
                        putExtra(Intent.EXTRA_TEXT, playlistVideoUrls)
                        putExtra("template", "%(title)s [%(id)s].%(ext)s")
                        type = "text/plain"
                    }
                } else {
                    val joinedIds = playlistVideoUrls.split("\n").mapNotNull { it.toID() }.joinToString(",")
                    Intent(Intent.ACTION_VIEW)
                        .setPackage(externalProviderPackageName)
                        .setDataAndType(
                            "${ShareDialog.YOUTUBE_FRONTEND_URL}/watch_videos?video_ids=${joinedIds}".toUri(),
                            VIDEO_MIMETYPE
                        )
                }

                withContext(Dispatchers.Main) {
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    fun extractDownloadInfoText(context: Context, download: DownloadWithItems): List<String> {
        val downloadInfo = mutableListOf<String>()
        download.downloadItems.firstOrNull { it.type == FileType.VIDEO }?.let { videoItem ->
            downloadInfo.add(context.getString(R.string.video) + ": ${videoItem.format} ${videoItem.quality}")
        }
        download.downloadItems.firstOrNull { it.type == FileType.AUDIO }?.let { audioItem ->
            var infoString = ": ${audioItem.quality} ${audioItem.format})"
            if (audioItem.language != null) infoString += " ${audioItem.language}"
            downloadInfo.add(context.getString(R.string.audio) + infoString)
        }
        download.downloadItems.firstOrNull { it.type == FileType.SUBTITLE }?.let {
            downloadInfo.add(context.getString(R.string.captions) + ": ${it.language}")
        }
        return downloadInfo
    }

    suspend fun deleteDownloadIncludingFiles(context: Context, downloadWithItems: DownloadWithItems) {
        val download = downloadWithItems.download
        val items = downloadWithItems.downloadItems

        items.forEach { item ->
            try {
                // 1. Try deleting the path stored in DB
                val deleted = item.path.deleteIfExists()
                com.github.libretube.logger.FileLogger.d("DownloadHelper", "Deleted ${item.path}: $deleted")

                // 2. Fallback: If DB path didn't exist (or was wrong), try Default/Private directory
                // This catches half-finished downloads that haven't updated their path in DB yet.
                if (!deleted) {
                     val subFolder = when(item.type) {
                        FileType.AUDIO -> AUDIO_DIR
                        FileType.VIDEO -> VIDEO_DIR
                        FileType.SUBTITLE -> SUBTITLE_DIR
                        else -> VIDEO_DIR
                    }
                    val privateFile = File(context.getExternalFilesDir(null), "$subFolder/${item.fileName}")
                    if (privateFile.exists()) {
                         val privDeleted = privateFile.delete()
                         com.github.libretube.logger.FileLogger.d("DownloadHelper", "Fallback: Deleted private file ${privateFile.absolutePath}: $privDeleted")
                    }
                }
            } catch (e: Exception) {
                 com.github.libretube.logger.FileLogger.e("DownloadHelper", "Failed to delete ${item.path}", e)
            }
        }
        runCatching {
            download.thumbnailPath?.deleteIfExists()
        }

        withContext(Dispatchers.IO) {
            DatabaseHolder.Database.downloadDao().deleteDownload(download)
        }
    }

    /**
     * Scans the public LibreTube folder and matches files to the database.
     * Supports both Filename Regex and ID3v2.4 Metadata.
     */
    suspend fun syncExternalFiles(context: Context) = withContext(Dispatchers.IO) {
        val publicDir = getDownloadDir(context, "")
        if (!java.nio.file.Files.exists(publicDir)) return@withContext

        val videoIdRegex = Regex(".*\\[(.{11})\\].*")
        val mediaRetriever = android.media.MediaMetadataRetriever()

        val files = try {
            java.nio.file.Files.walk(publicDir).use { stream ->
                stream.asSequence().filter { java.nio.file.Files.isRegularFile(it) }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        files.forEach { path ->
            val fileName = path.fileName.toString()
            var videoId: String? = null

            // Level 1: Filename matches Title [VIDEO_ID].ext
            val match = videoIdRegex.find(fileName)
            if (match != null) {
                videoId = match.groupValues[1]
            }

            // Level 2: ID3v2.4 / Media Metadata Deep Scan
            if (videoId == null) {
                try {
                    mediaRetriever.setDataSource(path.toString())
                    // Check Artist or Composer for the ID (common for YTDL)
                    val comment = mediaRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                        ?: mediaRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                        ?: mediaRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    
                    videoId = comment?.let { videoIdRegex.find(it)?.groupValues?.get(1) }
                } catch (e: Exception) {
                    // Not a media file or encrypted
                }
            }

            if (videoId != null) {
                linkFileToDatabase(context, videoId, path)
            }
        }
        mediaRetriever.release()
    }

    private suspend fun linkFileToDatabase(context: Context, videoId: String, path: Path) {
        val dao = DatabaseHolder.Database.downloadDao()
        val existing = dao.findById(videoId)
        
        if (existing == null) {
            // Create a stub Download entry if it doesn't exist (Synced from external)
            // In a real scenario, we might want to fetch metadata here, but for now just link it.
        } else {
            // Check if this path is already linked
            val isLinked = existing.downloadItems.any { it.path == path }
            if (!isLinked) {
                val fileType = when {
                    path.toString().endsWith(".mp3") || path.toString().endsWith(".m4a") -> FileType.AUDIO
                    else -> FileType.VIDEO
                }
                val newItem = DownloadItem(
                    type = fileType,
                    videoId = videoId,
                    fileName = path.fileName.toString(),
                    path = path,
                    downloadSize = java.nio.file.Files.size(path)
                )
                dao.insertDownloadItem(newItem)
                Log.d("DownloadHelper", "Linked external file $path to video $videoId")
            }
        }
    }
}

