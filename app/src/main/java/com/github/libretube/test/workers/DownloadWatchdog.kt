package com.github.libretube.test.workers

import android.content.Context
import androidx.work.*
import com.github.libretube.test.helpers.DownloadManager
import java.util.concurrent.TimeUnit

class DownloadWatchdog(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Run syncStatus for all known system downloads
        // DownloadManager.syncAllStatuses(applicationContext) -- Deprecated for Custom Engine
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "DownloadWatchdog"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DownloadWatchdog>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
