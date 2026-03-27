package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.interceptor.ResponseLoggingInterceptor
import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient

object ApiClientFactory {
    fun getClient(disableSsl: Boolean, apiKey: String, debugMode: Boolean): OkHttpClient {
        val authInterceptor = interceptor(apiKey)
        val builder = if (disableSsl) UnsafeOkHttpClient.unsafeOkHttpClient() else OkHttpClient.Builder()
        builder.addInterceptor(authInterceptor)
        return if(debugMode) builder.addInterceptor(ResponseLoggingInterceptor()).build() else builder.build()
    }

    fun getUnauthClient(disableSsl: Boolean): OkHttpClient {
        val builder = if (disableSsl) UnsafeOkHttpClient.unsafeOkHttpClient() else OkHttpClient.Builder()
        return builder.build()
    }

    private fun interceptor(apiKey: String): Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
        if (apiKey.startsWith("Bearer:")) {
            newRequest.addHeader("Authorization", "Bearer ${apiKey.removePrefix("Bearer:")}")
        } else {
            newRequest.addHeader("x-api-key", apiKey.trim())
        }
        chain.proceed(newRequest.build())
    }
}
