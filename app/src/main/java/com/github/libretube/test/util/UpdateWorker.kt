package com.github.libretube.test.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.libretube.test.R
import com.github.libretube.test.api.RetrofitInstance
import com.github.libretube.test.ui.activities.MainActivity
import com.github.libretube.test.obj.update.UpdateInfo
import com.github.libretube.test.BuildConfig

class UpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val isExperimental = BuildConfig.IS_EXPERIMENTAL
            val response = if (isExperimental) {
                RetrofitInstance.externalApi.getReleaseByTag("experimental")
            } else {
                RetrofitInstance.externalApi.getLatestRelease()
            }

            val currentVersionName = BuildConfig.VERSION_NAME
            val runPattern = Regex("(?:Run|Build)[\\s-]*(\\d+)", RegexOption.IGNORE_CASE)

            val currentRunNumber = runPattern.find(currentVersionName)?.groupValues?.get(1)?.toIntOrNull()
                ?: currentVersionName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

            val remoteRunNumber = runPattern.find(response.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (response.name.all { it.isDigit() }) response.name.toIntOrNull() ?: 0 else 0

            if (remoteRunNumber > currentRunNumber) {
                showUpdateNotification(response)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateInfo) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "update_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.update_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Pass necessary data to open the update dialog
            // Implementation depends on how MainActivity handles it, usually checking a flag or checking for update on start
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_download) // Ensure this icon exists or use a valid one
            .setContentTitle(applicationContext.getString(R.string.update_available))
            .setContentText("Version ${updateInfo.name} is available.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }
}

