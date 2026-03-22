package com.immichtv.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ImmichClient {

    private var api: ImmichApi? = null
    private var currentBaseUrl: String = ""
    private var currentApiKey: String = ""

    val baseUrl: String get() = currentBaseUrl

    fun configure(baseUrl: String, apiKey: String) {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedUrl == currentBaseUrl && apiKey == currentApiKey && api != null) return

        currentBaseUrl = normalizedUrl
        currentApiKey = apiKey

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", apiKey)
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ImmichApi::class.java)
    }

    fun getApi(): ImmichApi {
        return api ?: throw IllegalStateException(
            "ImmichClient not configured. Call configure(baseUrl, apiKey) first."
        )
    }

    // ── URL Builders ────────────────────────────────────────────────────────

    fun thumbnailUrl(assetId: String): String {
        return "${currentBaseUrl}api/assets/$assetId/thumbnail?size=preview"
    }

    fun thumbnailSmallUrl(assetId: String): String {
        return "${currentBaseUrl}api/assets/$assetId/thumbnail?size=thumbnail"
    }

    fun originalUrl(assetId: String): String {
        return "${currentBaseUrl}api/assets/$assetId/original"
    }

    fun videoPlaybackUrl(assetId: String): String {
        return "${currentBaseUrl}api/assets/$assetId/video/playback"
    }

    fun personThumbnailUrl(personId: String): String {
        return "${currentBaseUrl}api/people/$personId/thumbnail"
    }

    /** Returns an OkHttp-compatible header map for Glide / ExoPlayer */
    fun authHeaders(): Map<String, String> {
        return mapOf("x-api-key" to currentApiKey)
    }

    fun isConfigured(): Boolean = api != null
}
