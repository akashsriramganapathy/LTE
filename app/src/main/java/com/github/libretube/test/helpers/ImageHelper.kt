package com.github.libretube.test.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.load
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.github.libretube.test.BuildConfig
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.extensions.toAndroidUri
import com.github.libretube.test.util.DataSaverMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.nio.file.Path

object ImageHelper {
    lateinit var imageLoader: ImageLoader

    private val Context.coilFile get() = cacheDir.resolve("coil")
    private const val HTTP_SCHEME = "http"

    /**
     * Initialize the image loader
     */
    fun initializeImageLoader(context: Context) {
        val maxCacheSize = PreferenceHelper.getString(PreferenceKeys.MAX_IMAGE_CACHE, "128")

        val httpClient = OkHttpClient().newBuilder()

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            httpClient.addInterceptor(loggingInterceptor)
        }

        // Add User-Agent Interceptor to match Downloader/Extractor and avoid Bot Detection
        httpClient.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
                .build()
            chain.proceed(request)
        }

        imageLoader = ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(httpClient.build())
                )
            }
            .apply {
                if (maxCacheSize.isEmpty()) {
                    diskCachePolicy(CachePolicy.DISABLED)
                } else {
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)

                    val diskCache = generateDiskCache(
                        directory = context.coilFile,
                        size = maxCacheSize.toInt()
                    )
                    diskCache(diskCache)
                }
            }
            .build()
    }

    private fun generateDiskCache(directory: File, size: Int): DiskCache {
        return DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(size * 1024 * 1024L)
            .build()
    }

    /**
     * Checks if the corresponding image for the given key (e.g. a url) is cached.
     */
    private fun isCached(key: String): Boolean {
        val cacheSnapshot = imageLoader.diskCache?.openSnapshot(key)
        val isCacheHit = cacheSnapshot?.data?.toFile()?.exists()
        cacheSnapshot?.close()

        return isCacheHit ?: false
    }

    /**
     * load an image from a url into an imageView
     */
    fun loadImage(url: String?, target: ImageView, whiteBackground: Boolean = false) {
        if (url.isNullOrEmpty()) return

        // clear image to avoid loading issues at fast scrolling
        target.setImageBitmap(null)

        val urlToLoad = url
        android.util.Log.d("ImageHelper", "Loading image from URL: $urlToLoad")

        // only load online images if the data saver mode is disabled
        if (DataSaverMode.isEnabled(target.context)) {
            if (urlToLoad.startsWith(HTTP_SCHEME) && !isCached(urlToLoad)) return
        }

        target.load(urlToLoad) {
            listener(
                onSuccess = { _, _ ->
                    // set the background to white for transparent images
                    if (whiteBackground) target.setBackgroundColor(Color.WHITE)
                },
                onError = { _, result ->
                    // Fallback logic for DeArrow thumbnails
                    if (urlToLoad.contains("dearrow-thumb.ajay.app")) {
                        android.util.Log.w("ImageHelper", "DeArrow thumbnail failed (likely 204): $urlToLoad, falling back...")
                         // Extract video ID from DeArrow URL
                         val regex = Regex("(?:videoID=)([a-zA-Z0-9_-]{11})")
                         val videoId = regex.find(urlToLoad)?.groupValues?.get(1)
                         
                         if (videoId != null) {
                             val fallbackUrl = "https://i.ytimg.com/vi/$videoId/mq2.jpg"
                             android.util.Log.d("ImageHelper", "Falling back to original thumbnail: $fallbackUrl")
                             // Recursively load the fallback URL
                             // using target.post to avoid state issues during callback
                             target.post {
                                 loadImage(fallbackUrl, target, whiteBackground)
                             }
                         }
                    } else {
                        android.util.Log.e("ImageHelper", "Failed to load image: $urlToLoad, reason: ${result.throwable.message}")
                    }
                }
            )
        }
    }

    suspend fun downloadImage(context: Context, url: String, path: Path) {
        var bitmap = getImage(context, url)
        
        // Fallback Logic for DeArrow (Ported from loadImage)
        if (bitmap == null && url.contains("dearrow-thumb.ajay.app")) {
             val regex = Regex("(?:videoID=)([a-zA-Z0-9_-]{11})")
             val videoId = regex.find(url)?.groupValues?.get(1)
             
             if (videoId != null) {
                 val fallbackUrl = "https://i.ytimg.com/vi/$videoId/mq2.jpg"
                 android.util.Log.w("ImageHelper", "DeArrow failed. Falling back to original thumbnail: $fallbackUrl")
                 bitmap = getImage(context, fallbackUrl)
             }
        }

        if (bitmap == null) {
            android.util.Log.e("ImageHelper", "Failed to download bitmap for: $url (and fallback failed)")
            return
        }
        
        val finalBitmap = bitmap
        withContext(Dispatchers.IO) {
            try {
                // Direct File Write (Private Storage) - Bypasses ContentResolver issues
                val file = path.toFile()
                file.parentFile?.mkdirs()
                
                java.io.FileOutputStream(file).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                android.util.Log.d("ImageHelper", "Thumbnail saved successfully to: $path")
            } catch (e: Exception) {
                android.util.Log.e("ImageHelper", "Failed to save thumbnail to disk: $path", e)
                throw e
            }
        }
    }

    suspend fun getImage(context: Context, url: String?): Bitmap? {
        return getImage(context, url?.toUri())
    }

    suspend fun getImage(context: Context, url: Uri?): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()
        
        var bitmap = imageLoader.execute(request).image?.toBitmap()

        // Fallback Logic for DeArrow (Ported from loadImage/downloadImage)
        if (bitmap == null && url.toString().contains("dearrow-thumb.ajay.app")) {
            val urlString = url.toString()
            val regex = Regex("(?:videoID=)([a-zA-Z0-9_-]{11})")
            val videoId = regex.find(urlString)?.groupValues?.get(1)

            if (videoId != null) {
                val fallbackUrl = "https://i.ytimg.com/vi/$videoId/mq2.jpg"
                android.util.Log.w("ImageHelper", "getImage: DeArrow failed for $urlString. Falling back to: $fallbackUrl")
                bitmap = getImage(context, fallbackUrl.toUri())
            }
        }

        return bitmap
    }

    /**
     * Get a squared bitmap with the same width and height from a bitmap
     * @param bitmap The bitmap to resize
     */
    fun getSquareBitmap(bitmap: Bitmap): Bitmap {
        val newSize = minOf(bitmap.width, bitmap.height)
        return Bitmap.createBitmap(
            bitmap,
            (bitmap.width - newSize) / 2,
            (bitmap.height - newSize) / 2,
            newSize,
            newSize
        )
    }
}

