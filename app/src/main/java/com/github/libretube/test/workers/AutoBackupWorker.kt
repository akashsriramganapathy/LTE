package com.github.libretube.test.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.helpers.BackupHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.obj.BackupFile
import com.github.libretube.test.obj.PreferenceItem
import com.github.libretube.test.util.TextUtils
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import com.github.libretube.test.api.JsonHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import com.github.libretube.logger.FileLogger
import java.io.PrintWriter
import java.io.StringWriter

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!PreferenceHelper.getBoolean(PreferenceKeys.AUTO_BACKUP_ENABLED, false)) {
                return@withContext Result.success()
            }

        val backupPath = PreferenceHelper.getString(PreferenceKeys.AUTO_BACKUP_PATH, "")
        if (backupPath.isEmpty()) {
            FileLogger.e(TAG, "Auto Backup skipped: Path not set")
            return@withContext Result.failure()
        }

        val uri = Uri.parse(backupPath)
        val tree = DocumentFile.fromTreeUri(applicationContext, uri)
        if (tree == null || !tree.canWrite()) {
            FileLogger.e(TAG, "Cannot write to backup location: $backupPath")
            return@withContext Result.failure()
        }
        
        // Force DB Backup always
        val timestamp = TextUtils.getFileSafeTimeStampNow()
        
        FileLogger.i(TAG, "Starting Auto Backup (DB Export)...")
        val fileName = "libretube-auto-backup-$timestamp.db"
        val file = tree.createFile("application/x-sqlite3", fileName)
        if (file == null) {
            FileLogger.e(TAG, "Failed to create backup file")
            return@withContext Result.failure()
        }

        val success = com.github.libretube.test.helpers.DatabaseExportHelper.exportDatabase(applicationContext, file.uri)
        if (success) {
            FileLogger.i(TAG, "Backup written successfully to $fileName")
            pruneBackups(tree)
            return@withContext Result.success()
        } else {
            FileLogger.e(TAG, "Failed to export database")
            file.delete()
            return@withContext Result.failure()
        }

        } catch (e: Throwable) {
            FileLogger.e(TAG, "Critical error during auto backup", e)
            
            // Attempt to write error log to the backup folder
            try {
                val backupPath = PreferenceHelper.getString(PreferenceKeys.AUTO_BACKUP_PATH, "")
                if (backupPath.isNotEmpty()) {
                    val uri = Uri.parse(backupPath)
                    val tree = DocumentFile.fromTreeUri(applicationContext, uri)
                    if (tree != null && tree.canWrite()) {
                        val errorFile = tree.createFile("text/plain", "libretube-backup-error.txt")
                        if (errorFile != null) {
                            applicationContext.contentResolver.openOutputStream(errorFile.uri)?.use { output ->
                                PrintWriter(output.bufferedWriter()).use { writer ->
                                    writer.println("Auto Backup Failed at ${java.util.Date()}")
                                    e.printStackTrace(writer)
                                }
                            }
                        }
                    }
                }
            } catch (loggingError: Exception) {
                FileLogger.e(TAG, "Failed to write error log", loggingError)
            }
            
            return@withContext Result.failure()
        }
    }

    private fun pruneBackups(tree: DocumentFile) {
        val maxFilesStr = PreferenceHelper.getString(PreferenceKeys.AUTO_BACKUP_MAX_FILES, "3")
        val maxFiles = maxFilesStr.toIntOrNull() ?: 3
        
        val files = tree.listFiles().filter { 
            it.name?.startsWith("libretube-auto-backup-") == true && (it.name?.endsWith(".json") == true || it.name?.endsWith(".db") == true)
        }.sortedByDescending { it.lastModified() } // Newest first

        if (files.size > maxFiles) {
            val toDelete = files.drop(maxFiles)
            FileLogger.d(TAG, "Pruning ${toDelete.size} old backups")
            toDelete.forEach { it.delete() }
        }
    }

    companion object {
        const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "auto_backup_work"
        const val AUTO_BACKUP_MAX_FILES_DEFAULT = 3

        fun enqueueWork(context: Context, existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE) {
            val targetTime = PreferenceHelper.getString(PreferenceKeys.AUTO_BACKUP_TIME, "02:00")
            val parts = targetTime.split(":")
            val targetHour = parts.getOrElse(0) { "02" }.toInt()
            val targetMinute = parts.getOrElse(1) { "00" }.toInt()

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelay = target.timeInMillis - now.timeInMillis
            
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            FileLogger.d(TAG, "Scheduling Auto Backup for ${dateFormat.format(target.time)} (in ${initialDelay / 1000}s)")

            val cleanupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                24, TimeUnit.HOURS
            ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
             .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                existingPeriodicWorkPolicy,
                cleanupRequest
            )
        }

        fun runNow(context: Context) {
            FileLogger.i(TAG, "Manual AutoBackup Triggered")
            val request = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

