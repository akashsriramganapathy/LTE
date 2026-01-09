package com.github.libretube.test.util

import android.content.Context
import android.os.Build
import com.github.libretube.test.BuildConfig
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.PreferenceHelper
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatRecorder {
    private var process: Process? = null
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "logcat.txt"

    // 1MB rotation, 5 files = 5MB max
    private const val ROTATE_SIZE_KB = 1024
    private const val ROTATE_COUNT = 5

    fun start(context: Context) {
        // Only start if enabled
        if (!PreferenceHelper.getBoolean(PreferenceKeys.ENABLE_ROBUST_LOGGING, false)) {
            return
        }

        // Stop any existing process to be safe
        stop()

        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val logFile = File(logDir, LOG_FILE_NAME)

        // Write Header
        writeHeader(logFile)

        try {
            // -f: File
            // -r: Rotate size (KB)
            // -n: Rotate count
            // -v threadtime: Verbose format with time/PID/TID
            // *:V : All tags, Verbose level
            val command = "logcat -f ${logFile.absolutePath} -r $ROTATE_SIZE_KB -n $ROTATE_COUNT -v threadtime *:V"
            process = Runtime.getRuntime().exec(command)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    fun getLogFile(context: Context): File {
        return File(File(context.getExternalFilesDir(null), LOG_DIR_NAME), LOG_FILE_NAME)
    }

    fun getLogContent(context: Context): String {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        val sb = StringBuilder()
        
        // Get the clear timestamp (default 0L means show everything)
        // Adjust for current year because logcat only gives MM-DD
        val clearTime = PreferenceHelper.getLong(PreferenceKeys.LOG_VIEWER_START_TIMESTAMP, 0L)
        
        // Date parser for "MM-dd HH:mm:ss.SSS"
        // We need to assume current year
        val currentYear = SimpleDateFormat("yyyy", Locale.US).format(Date())

        fun processFile(file: File) {
             if (!file.exists()) return
             
             // If we haven't cleared, just dump everything (fast path)
             if (clearTime == 0L) {
                 if (sb.length > 5 * 1024 * 1024) return // Safety limit 5MB
                 
                 if (sb.isNotEmpty()) sb.append("\n\n")
                 sb.append("--- Source: ${file.name} ---\n")
                 // Use bufferedReader to avoid loading huge strings in memory at once
                 file.bufferedReader().use { reader ->
                     var line: String? = reader.readLine()
                     while (line != null) {
                         sb.append(line).append("\n")
                         if (sb.length > 5 * 1024 * 1024) break // Safety limit
                         line = reader.readLine()
                     }
                 }
                 return
             }
             
             // Slow path: line by line filtering
             var headerAdded = false
             file.useLines { lines ->
                 for (line in lines) {
                     if (sb.length > 5 * 1024 * 1024) break // Safety limit
                     
                     // Check if line starts with date pattern roughly (e.g. "01-05 18:00:00.000")
                     if (line.length > 18 && line[2] == '-' && line[5] == ' ') {
                         try {
                             val datePart = "$currentYear-${line.substring(0, 18)}"
                             val logDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).parse(datePart)
                             if (logDate != null && logDate.time >= clearTime) {
                                 if (!headerAdded) {
                                      if (sb.isNotEmpty()) sb.append("\n\n")
                                      sb.append("--- Source: ${file.name} ---\n")
                                      headerAdded = true
                                 }
                                 sb.append(line).append("\n")
                             }
                         } catch (e: Exception) {
                             if (headerAdded) {
                                 sb.append(line).append("\n")
                             }
                         }
                     } else if (headerAdded) {
                         sb.append(line).append("\n")
                     }
                 }
             }
        }

        // Read rotated files first (oldest to newest)
        for (i in ROTATE_COUNT - 1 downTo 1) {
            processFile(File(logDir, "$LOG_FILE_NAME.$i"))
        }

        // Read current file
        processFile(File(logDir, LOG_FILE_NAME))

        return sb.toString()
    }

    private fun writeHeader(file: File) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = """
            
            ==================================================
            LibreTube Robust Log Capture
            Start Time: $time
            App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})
            App ID: ${BuildConfig.APPLICATION_ID}
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            ==================================================
            
        """.trimIndent()

        try {
            // Append header to file (or create if not exists)
            file.appendText(header)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
