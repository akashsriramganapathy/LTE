package com.github.libretube.test.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.github.libretube.logger.FileLogger
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.db.obj.DownloadItem
import com.github.libretube.test.enums.FileType
import com.github.libretube.test.extensions.toastFromMainThread
import com.github.libretube.test.obj.DownloadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DownloadManager {
    private val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Custom Dispatcher for Concurrency Control (Dynamic)
    private val executorService = Executors.newFixedThreadPool(8) as java.util.concurrent.ThreadPoolExecutor
    private val downloadDispatcher = executorService.asCoroutineDispatcher()
    
    fun updateConcurrency(maxThreads: Int) {
        val count = maxThreads.coerceIn(1, 10) // Safety clamp
        if (executorService.corePoolSize != count) {
            executorService.corePoolSize = count
            executorService.maximumPoolSize = count
            FileLogger.d(TAG, "Updated Download Concurrency to $count")
        }
    }

    private val _downloadFlow = MutableSharedFlow<Pair<Int, DownloadStatus>>()
    val downloadFlow: SharedFlow<Pair<Int, DownloadStatus>> = _downloadFlow

    // Track active jobs to allow cancellation
    private val activeJobs = ConcurrentHashMap<Int, Job>()

    // Unstoppable Client: No Call Timeout, only Read/Connect timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Use Desktop Firefox to match NewPipe Extractor (Fixes 80KB/s Throttling)
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"
    
    // 10MB Chunk Size to bypass throttling
    private const val CHUNK_SIZE = 10 * 1024 * 1024L 

    fun enqueue(context: Context, item: DownloadItem) {
        // Avoid Dispatchers.Main to prevent UI stutter
        startDownload(context, item)
    }

    private fun startDownload(context: Context, item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) {
            FileLogger.w(TAG, "Download ${item.id} is already running.")
            return
        }

        val job = scope.launch(downloadDispatcher) {
            try {
                // Determine Path
                val subFolder = when(item.type) {
                    FileType.AUDIO -> "Audio"
                    else -> "Video"
                }
                // Use App-Specific PRIVATE Storage (Android/data/...) as requested ("data file")
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val dir = File(baseDir, subFolder)
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, item.fileName)
                item.path = file.toPath()
                // Reset systemId to -1 (Running) in case this is a retry of a failed/completed item
                item.systemId = -1
                // Update DB with initial path
                Database.downloadDao().updateDownloadItem(item)

                downloadLoop(item, file)

            } catch (e: CancellationException) {
                FileLogger.i(TAG, "Download cancelled: ${item.fileName}")
                _downloadFlow.emit(item.id to DownloadStatus.Stopped)
            } catch (e: Exception) {
                FileLogger.e(TAG, "Fatal error in download: ${item.fileName}", e)
                // Mark as Error in DB so it doesn't count as "Pending"
                item.systemId = -3 
                Database.downloadDao().updateDownloadItem(item)
                _downloadFlow.emit(item.id to DownloadStatus.Error(e.message ?: "Unknown Error"))
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    private suspend fun downloadLoop(item: DownloadItem, file: File) {
        var isComplete = false
        var retryCount = 0
        val startTime = System.currentTimeMillis()
        
        // Notify Starting
        _downloadFlow.emit(item.id to DownloadStatus.Progress(0, file.length(), item.downloadSize))

        while (!isComplete && CoroutineScope(Dispatchers.IO).isActive) {
            try {
                // 1. Check existing size (Resume Point)
                val downloadedBytes = if (file.exists()) file.length() else 0L
                
                if (item.downloadSize > 0 && downloadedBytes >= item.downloadSize) {
                    FileLogger.i(TAG, "File already fully downloaded: ${item.fileName}")
                    isComplete = true
                    break
                }

                if (item.url.isNullOrEmpty()) throw IllegalArgumentException("URL is null")

                // Determine Chunk Range
                // If we don't know total size, we can't chunk properly first request, but usually we do or get it soon.
                // Request 10MB from current position
                val chunkEnd = if (item.downloadSize > 0) {
                     (downloadedBytes + CHUNK_SIZE - 1).coerceAtMost(item.downloadSize - 1)
                } else {
                     -1L // Open ended if unknown
                }
                
                // If specific chunk, use it. If unknown size, just range from current.
                val rangeHeader = if (chunkEnd > 0) "bytes=$downloadedBytes-$chunkEnd" else "bytes=$downloadedBytes-"

                FileLogger.d(TAG, "Download ${item.id}: Requesting $rangeHeader")

                // 2. Build Request
                val requestBuilder = Request.Builder()
                    .url(item.url!!)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.youtube.com/")
                    .header("Range", rangeHeader)

                // 3. Execute
                val response = client.newCall(requestBuilder.build()).execute()
                
                if (!response.isSuccessful) {
                    // 416 Range Not Satisfiable: Likely completed 
                    if (response.code == 416) {
                        FileLogger.w(TAG, "Server returned 416 (Range Not Satisfiable). Assuming completion.")
                        isComplete = true
                        response.close()
                        break
                    }
                    response.close()
                    throw java.io.IOException("Server returned ${response.code}")
                }

                // Update Total Size if we didn't know it
                if (item.downloadSize <= 0) {
                    val contentLength = response.body?.contentLength() ?: -1L
                    if (contentLength > 0) {
                        // Content-Range: bytes 0-100/2000
                        val contentRange = response.header("Content-Range")
                        if (contentRange != null) {
                             val total = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                             if (total > 0 && total != item.downloadSize) {
                                  item.downloadSize = total
                                  Database.downloadDao().updateDownloadItem(item)
                             }
                        } else {
                             // Fallback if no Range set (should not happen with bytes=request)
                             val totalSize = downloadedBytes + contentLength
                             item.downloadSize = totalSize
                             Database.downloadDao().updateDownloadItem(item)
                        }
                    }
                }

                // 4. Stream Data
                val inputStream: InputStream = response.body!!.byteStream()
                val outputStream = FileOutputStream(file, true) // Append Mode!
                // Increase Buffer to 512KB for better throughput
                val buffer = ByteArray(512 * 1024) 
                var bytesRead: Int
                var bytesSinceLastUpdate = 0L

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!activeJobs.containsKey(item.id)) throw CancellationException("Job removed from activeJobs")

                        outputStream.write(buffer, 0, bytesRead)
                        
                        // Update Progress
                        val currentSize = file.length()
                        bytesSinceLastUpdate += bytesRead
                        
                        // Throttle updates (every 2MB to reduce overhead)
                        if (bytesSinceLastUpdate > 2 * 1024 * 1024) {
                             _downloadFlow.emit(item.id to DownloadStatus.Progress(0, currentSize, item.downloadSize))
                             bytesSinceLastUpdate = 0
                        }
                    }
                    
                    // Chunk finished success 
                    // Verify if we need more chunks
                    if (item.downloadSize > 0 && file.length() >= item.downloadSize) {
                        isComplete = true
                    } else {
                         // Loop continues for next chunk
                    }

                } finally {
                    try { inputStream.close() } catch(e:Exception){}
                    try { outputStream.flush(); outputStream.close() } catch(e:Exception){}
                    try { response.close() } catch(e:Exception){}
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                
                FileLogger.w(TAG, "Download interrupted: ${e.message}. Waiting 2s before resume...")
                retryCount++
                _downloadFlow.emit(item.id to DownloadStatus.Progress(0, file.length(), item.downloadSize)) // Update status
                
                delay(2000) // Wait 2s before retry
            }
        }

        if (isComplete) {
            item.systemId = -2 // Internal Completed
            item.downloadSize = file.length()
            Database.downloadDao().updateDownloadItem(item)
            _downloadFlow.emit(item.id to DownloadStatus.Completed)
            val duration = (System.currentTimeMillis() - startTime) / 1000
            FileLogger.i(TAG, "Download Finished: ${item.fileName} in ${duration}s")
        }
    }

    fun cancel(context: Context, item: DownloadItem) {
        val job = activeJobs[item.id]
        if (job != null) {
            job.cancel()
            activeJobs.remove(item.id)
        }
        scope.launch {
            Database.downloadDao().deleteDownloadItemById(item.id)
            _downloadFlow.emit(item.id to DownloadStatus.Stopped)
        }
    }
}
