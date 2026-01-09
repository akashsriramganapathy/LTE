package com.github.libretube.test.services

import android.app.NotificationManager
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.LibreTubeApp.Companion.DOWNLOAD_CHANNEL_NAME
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.obj.Streams
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.db.obj.Download
import com.github.libretube.test.db.obj.DownloadChapter
import com.github.libretube.test.db.obj.DownloadItem
import com.github.libretube.test.enums.FileType
import com.github.libretube.test.enums.NotificationId
import com.github.libretube.test.extensions.formatAsFileSize
import com.github.libretube.test.extensions.parcelableExtra
import com.github.libretube.test.extensions.toLocalDate
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.DownloadHelper
import com.github.libretube.test.helpers.DownloadManager
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.obj.DownloadStatus
import com.github.libretube.test.parcelable.DownloadData
import com.github.libretube.test.receivers.NotificationReceiver
import com.github.libretube.test.receivers.NotificationReceiver.Companion.ACTION_DOWNLOAD_PAUSE
import com.github.libretube.test.receivers.NotificationReceiver.Companion.ACTION_DOWNLOAD_RESUME
import com.github.libretube.test.receivers.NotificationReceiver.Companion.ACTION_DOWNLOAD_STOP
import com.github.libretube.test.ui.activities.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.*

/**
 * Download Service Refacted for Native Manager
 * Now acts as a lightweight coordinator.
 */
class DownloadService : LifecycleService() {
    private val binder = LocalBinder()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val coroutineContext = dispatcher + SupervisorJob()

    private lateinit var notificationManager: NotificationManager
    private lateinit var summaryNotificationBuilder: Builder

    // Cache for active download titles: ID -> Title
    private val activeTitles = java.util.concurrent.ConcurrentHashMap<Int, String>()
    // Cache for last progress to calculate speed (simple approximation) : ID -> (Bytes, Timestamp)
    private val progressCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, Long>>()


    private val _downloadFlow = MutableSharedFlow<Pair<Int, DownloadStatus>>()
    val downloadFlow: SharedFlow<Pair<Int, DownloadStatus>> = _downloadFlow

    override fun onCreate() {
        super.onCreate()
        IS_DOWNLOAD_RUNNING = true
        notifyForeground()
        sendBroadcast(Intent(ACTION_SERVICE_STARTED))
        
        lifecycleScope.launch(coroutineContext) {
            updateForegroundNotification()
        }

        lifecycleScope.launch(coroutineContext) {
            DownloadManager.downloadFlow.collect { (id: Int, status: DownloadStatus) ->
                handleDownloadStatus(id, status)
            }
        }

    }
    
    // Removed System DownloadManager receiver as we use custom downloader now.

    private suspend fun handleDownloadStatus(id: Int, status: DownloadStatus) {
        _downloadFlow.emit(id to status)
        val shouldUpdateCount = status !is DownloadStatus.Progress
        if (shouldUpdateCount) {
            updateForegroundNotification()
        }
        when (status) {
            is DownloadStatus.Completed, is DownloadStatus.Error, is DownloadStatus.Stopped -> {
                activeTitles.remove(id)
                progressCache.remove(id)
                stopServiceIfDone()
                // Force update to remove specific title
                updateForegroundNotification() 
            }
            is DownloadStatus.Progress -> {
                // Throttle updates: Only update every 500ms or 1% ?
                // For now, let's just update as it comes, but maybe DownloadManager throttles it?
                // DownloadManager doesn't throttle flow emission much.
                updateForegroundNotification(id, status)
            }
            else -> {}
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        notifyForeground()
        
        val downloadId = intent?.getIntExtra("id", -1) ?: -1
        when (intent?.action) {
            ACTION_DOWNLOAD_RESUME -> {
                lifecycleScope.launch(coroutineContext) {
                    val item = Database.downloadDao().findDownloadItemById(downloadId)
                    item?.let { DownloadManager.enqueue(this@DownloadService, it) }
                }
            }
            ACTION_DOWNLOAD_PAUSE -> {
                lifecycleScope.launch(coroutineContext) {
                    val item = Database.downloadDao().findDownloadItemById(downloadId)
                    item?.let { DownloadManager.cancel(this@DownloadService, it) }
                }
            }
            ACTION_DOWNLOAD_STOP -> {
                lifecycleScope.launch(coroutineContext) {
                    val item = Database.downloadDao().findDownloadItemById(downloadId)
                    item?.let { DownloadManager.cancel(this@DownloadService, it) }
                }
            }
        }

        val downloadData = intent?.parcelableExtra<DownloadData>(IntentData.downloadData)
            ?: return START_NOT_STICKY

        // Handle New Download Request
        lifecycleScope.launch(coroutineContext) {
            val videoId = downloadData.videoId
            val streams = try {
                withContext(Dispatchers.IO) {
                    MediaServiceRepository.instance.getStreams(videoId)
                }
            } catch (e: Exception) {
                com.github.libretube.logger.FileLogger.e("DownloadService", "GetStreams failed", e)
                toastFromMainDispatcher(e.localizedMessage.orEmpty())
                stopServiceIfDone()
                return@launch
            }

            val downloadItems = streams.toDownloadItems(downloadData)
            val thumbnailTargetPath = getDownloadPath(DownloadHelper.THUMBNAIL_DIR, videoId)
            
            val download = Download(
                videoId,
                streams.title,
                streams.description,
                streams.uploader,
                streams.duration,
                streams.uploadTimestamp?.toLocalDate(),
                thumbnailTargetPath
            )
            
            val chapters = streams.chapters.map {
                DownloadChapter(videoId = videoId, name = it.title, start = it.start, thumbnailUrl = it.image)
            }

            for (item in downloadItems) {
                item.path = when (item.type) {
                    FileType.AUDIO -> getDownloadPath(DownloadHelper.AUDIO_DIR, item.fileName)
                    FileType.VIDEO -> getDownloadPath(DownloadHelper.VIDEO_DIR, item.fileName)
                    FileType.SUBTITLE -> getDownloadPath(DownloadHelper.SUBTITLE_DIR, item.fileName)
                    else -> throw IllegalArgumentException("Unknown file type")
                }
            }

            Database.downloadDao().insertDownloadWithItems(download, downloadItems, chapters)

            try {
                ImageHelper.downloadImage(this@DownloadService, streams.thumbnailUrl!!, thumbnailTargetPath)
            } catch (e: Exception) {
                com.github.libretube.logger.FileLogger.e("DownloadService", "Failed to download thumbnail", e)
            }

            // QUEUE ALL ITEMS (Native Manager handles concurrency)
            for (item in downloadItems) {
                DownloadManager.enqueue(this@DownloadService, item)
            }
            
            updateForegroundNotification()
        }

        return START_NOT_STICKY
    }

    fun isDownloading(id: Int): Boolean {
        // Since we use System DM, this basic check is tricky. 
        // Ideally checking ID existence in System DM is better but costly. 
        // We rely on service life for now.
        return true 
    }

    internal fun resume(id: Int) = lifecycleScope.launch(coroutineContext) {
        val item = Database.downloadDao().findDownloadItemById(id)
        item?.let { DownloadManager.enqueue(this@DownloadService, it) }
    }

    internal fun pause(id: Int) = lifecycleScope.launch(coroutineContext) {
        val item = Database.downloadDao().findDownloadItemById(id)
        item?.let { DownloadManager.cancel(this@DownloadService, it) }
    }

    private fun stopServiceIfDone() {
        lifecycleScope.launch { 
             delay(2000)
             val pending = Database.downloadDao().countPendingItems()
             if (pending == 0) {
                 notificationManager.cancel(NotificationId.DOWNLOAD_IN_PROGRESS.id)
                 ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                 sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
                 stopSelf()
             }
        }
    }

    private suspend fun updateForegroundNotification(
        downloadId: Int? = null,
        status: DownloadStatus? = null
    ) {
        val pendingCount = Database.downloadDao().countPendingItems()

        if (pendingCount == 0) {
            stopServiceIfDone()
            return
        }
        
        // Default text if nothing specific
        var title = getString(R.string.downloading)
        var text = getString(R.string.downloading_count, pendingCount)
        var progress = 0
        var indeterminate = true

        // If we have a specific status update
        if (downloadId != null && status != null && status is DownloadStatus.Progress) {
            // Get Title
            val itemTitle = activeTitles[downloadId] ?: withContext(Dispatchers.IO) {
                Database.downloadDao().findDownloadItemById(downloadId)?.fileName.also { 
                    if (it != null) activeTitles[downloadId] = it
                }
            } ?: "File"

            title = itemTitle
            
            // Calculate Percentage
            if (status.total > 0) {
                progress = ((status.downloaded * 100) / status.total).toInt()
                indeterminate = false
            }

            // Calculate Speed (Simple Average over last update)
            val now = System.currentTimeMillis()
            val lastUpdate = progressCache[downloadId]
            var speed = ""
            
            if (lastUpdate != null) {
                val diffBytes = status.downloaded - lastUpdate.first
                val diffTime = now - lastUpdate.second
                if (diffTime > 0) {
                    val speedBytesPerSec = (diffBytes * 1000) / diffTime
                    speed = "${speedBytesPerSec.formatAsFileSize()}/s"
                }
            }
            progressCache[downloadId] = status.downloaded to now

            text = if (speed.isNotEmpty()) {
                "$progress% • $speed"
            } else {
                "$progress%"
            }
            
            // If multiple downloads, append count
            if (pendingCount > 1) {
                text += " (+${pendingCount - 1} more)"
            }
        }

        summaryNotificationBuilder
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
        
        notificationManager.notify(
            NotificationId.DOWNLOAD_IN_PROGRESS.id,
            summaryNotificationBuilder.build()
        )
    }

    private fun notifyForeground() {
        notificationManager = getSystemService()!!

        summaryNotificationBuilder = Builder(this, DOWNLOAD_CHANNEL_NAME)
            .setSmallIcon(R.drawable.ic_launcher_lockscreen)
            .setContentTitle(getString(R.string.downloading))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        ServiceCompat.startForeground(
            this, NotificationId.DOWNLOAD_IN_PROGRESS.id, summaryNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun getDownloadPath(directory: String, fileName: String): Path {
        return DownloadHelper.getDownloadDir(this, directory) / fileName
    }

    override fun onDestroy() {
        IS_DOWNLOAD_RUNNING = false
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    companion object {
        const val ACTION_SERVICE_STARTED =
            "com.github.libretube.test.services.DownloadService.ACTION_SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED =
            "com.github.libretube.test.services.DownloadService.ACTION_SERVICE_STOPPED"
        
        var IS_DOWNLOAD_RUNNING = false
    }
}
