package com.github.libretube.test.util

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class NewPipeDownloaderImpl : Downloader() {
    private val cookieJar = object : okhttp3.CookieJar {
        private val cookieStore = HashMap<String, List<okhttp3.Cookie>>()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept-Language", "en-US,en;q=0.9")

        com.github.libretube.logger.FileLogger.d("NewPipeDownloader", "Request: $httpMethod $url")
        com.github.libretube.logger.FileLogger.d("NewPipeDownloader", "Headers: User-Agent=$USER_AGENT, Accept-Language=en-US,en;q=0.9")

        for ((headerKey, headerValues) in headers) {
            requestBuilder.removeHeader(headerKey)
            for (headerValue in headerValues) {
                requestBuilder.addHeader(headerKey, headerValue)
            }
        }
        val response = client.newCall(requestBuilder.build()).execute()

        return when (response.code) {
            429 -> {
                response.close()
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            else -> {
                val responseBodyToReturn = response.body?.string()
                Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBodyToReturn,
                    response.request.url.toString()
                )
            }
        }
    }

    companion object {
        // Using a standard, modern Desktop Firefox User-Agent.
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
    }
}
