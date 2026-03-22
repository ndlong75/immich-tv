package com.immichtv.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.immichtv.api.ImmichClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
class ImmichGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            // Only add auth header for requests to our Immich server
            val baseUrl = ImmichClient.baseUrl
            if (baseUrl.isNotEmpty() && original.url.toString().startsWith(baseUrl)) {
                val headers = ImmichClient.authHeaders()
                val builder = original.newBuilder()
                headers.forEach { (key, value) -> builder.addHeader(key, value) }
                chain.proceed(builder.build())
            } else {
                chain.proceed(original)
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Default options
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
