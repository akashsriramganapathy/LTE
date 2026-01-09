package com.github.libretube.test

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.NewPipeExtractorInstance
import com.github.libretube.test.helpers.NotificationHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.helpers.ShortcutHelper
import com.github.libretube.test.util.ExceptionHandler
import java.io.File

class LibreTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        /**
         * Initialize the needed notification channels for DownloadService and BackgroundMode
         */
        com.github.libretube.logger.FileLogger.init(applicationContext)
        initializeNotificationChannels()

        /**
         * Initialize the [PreferenceHelper]
         */
        com.github.libretube.test.helpers.RoomPreferenceDataStore.initializeBlocking()
        PreferenceHelper.initialize(applicationContext)
        PreferenceHelper.migrate()
        com.github.libretube.test.util.LogcatRecorder.start(applicationContext)

        /**
         * Set the api and the auth api url
         */
        ImageHelper.initializeImageLoader(this)

        /**
         * Initialize the notification listener in the background
         */
        NotificationHelper.enqueueWork(
            context = this,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        )

        // Schedule Auto Backup (if enabled)
        com.github.libretube.test.workers.AutoBackupWorker.enqueueWork(this, ExistingPeriodicWorkPolicy.KEEP)

        // Schedule Download Watchdog
        com.github.libretube.test.workers.DownloadWatchdog.schedule(this)

        /**
         * Handler for uncaught exceptions
         */
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val exceptionHandler = ExceptionHandler(defaultExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)

        /**
         * Dynamically create App Shortcuts
         */
        ShortcutHelper.createShortcuts(this)
        
        // Initialize Download Concurrency
        com.github.libretube.test.helpers.DownloadManager.updateConcurrency(
            PreferenceHelper.getInt(com.github.libretube.test.constants.PreferenceKeys.MAX_CONCURRENT_DOWNLOADS, 8)
        )

        NewPipeExtractorInstance.init()
        
        // YoutubeDL and FFmpeg initialization removed as part of native downloader refactor

        
        // Clean up old update APK if it exists
        val updateFile = File(getExternalFilesDir(null), "LibreTube-Update.apk")
        if (updateFile.exists()) {
            updateFile.delete()
        }
    }

    /**
     * Initializes the required notification channels for the app.
     */
    private fun initializeNotificationChannels() {
        val downloadChannel = NotificationChannelCompat.Builder(
            PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.download_playlist))
            .setDescription(getString(R.string.enqueue_playlist_description))
            .build()
        val playlistDownloadEnqueueChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.download_channel_name))
            .setDescription(getString(R.string.download_channel_description))
            .build()
        val playerChannel = NotificationChannelCompat.Builder(
            PLAYER_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.player_channel_name))
            .setDescription(getString(R.string.player_channel_description))
            .build()
        val pushChannel = NotificationChannelCompat.Builder(
            PUSH_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(getString(R.string.push_channel_name))
            .setDescription(getString(R.string.push_channel_description))
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelsCompat(
            listOf(
                downloadChannel,
                playlistDownloadEnqueueChannel,
                pushChannel,
                playerChannel
            )
        )
    }

    companion object {
        lateinit var instance: LibreTubeApp

        const val DOWNLOAD_CHANNEL_NAME = "download_service"
        const val PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME = "playlist_download_enqueue"
        const val PLAYER_CHANNEL_NAME = "player_mode"
        const val PUSH_CHANNEL_NAME = "notification_worker"
    }
}
