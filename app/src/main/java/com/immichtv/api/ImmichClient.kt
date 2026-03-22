package com.immichtv.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ImmichClient {

    private var api: ImmichApi? = null
    private var unauthApi: ImmichApi? = null  // For login (no auth header)
    private var currentBaseUrl: String = ""
    private var currentToken: String = ""

    val baseUrl: String get() = currentBaseUrl

    /** Create an unauthenticated client for login only */
    fun configureForLogin(baseUrl: String) {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        currentBaseUrl = normalizedUrl

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        unauthApi = retrofit.create(ImmichApi::class.java)
    }

    fun getUnauthApi(): ImmichApi {
        return unauthApi ?: throw IllegalStateException("Call configureForLogin first")
    }

    /** Configure authenticated client with Bearer token */
    fun configure(baseUrl: String, accessToken: String) {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedUrl == currentBaseUrl && accessToken == currentToken && api != null) return

        currentBaseUrl = normalizedUrl
        currentToken = accessToken

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
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
            "ImmichClient not configured. Login first."
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

    /** Returns auth headers for Glide / ExoPlayer */
    fun authHeaders(): Map<String, String> {
        return mapOf("Authorization" to "Bearer $currentToken")
    }

    fun isConfigured(): Boolean = api != null
}
