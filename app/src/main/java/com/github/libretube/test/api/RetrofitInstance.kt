package com.github.libretube.test.api

import com.github.libretube.test.BuildConfig
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.PreferenceHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

object RetrofitInstance {
    val apiLazyMgr = resettableManager()
    val kotlinxConverterFactory = JsonHelper.json
        .asConverterFactory("application/json".toMediaType())

    val httpClient by lazy { buildClient() }

    // the url provided here isn't actually used anywhere in the external api for SB/RYD/Github
    val externalApi = buildRetrofitInstance<ExternalApi>("https://sponsor.ajay.app")

    private fun buildClient(): OkHttpClient {
        val httpClient = OkHttpClient().newBuilder()

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            httpClient.addInterceptor(loggingInterceptor)
        }

        return httpClient.build()
    }

    inline fun <reified T: Any> buildRetrofitInstance(apiUrl: String): T = Retrofit.Builder()
        .baseUrl(apiUrl)
        .client(httpClient)
        .addConverterFactory(kotlinxConverterFactory)
        .build()
        .create<T>()
}

