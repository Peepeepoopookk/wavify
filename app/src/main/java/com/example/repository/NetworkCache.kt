package com.example.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File

private const val API_CACHE_BYTES = 10L * 1024L * 1024L
private const val NETWORK_MAX_AGE_SECONDS = 60
private const val OFFLINE_MAX_STALE_SECONDS = 60 * 60 * 24 * 7

internal fun cachedOkHttpClient(
    context: Context,
    builder: OkHttpClient.Builder = OkHttpClient.Builder()
): OkHttpClient {
    val appContext = context.applicationContext
    return builder
        .cache(Cache(File(appContext.cacheDir, "http_api_cache"), API_CACHE_BYTES))
        .addInterceptor(OfflineCacheInterceptor(appContext))
        .addNetworkInterceptor(GetResponseCacheInterceptor())
        .build()
}

internal fun hasNetwork(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

internal fun isUnmeteredNetwork(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

fun clearApiCache(context: Context) {
    File(context.applicationContext.cacheDir, "http_api_cache").deleteRecursively()
}

private class OfflineCacheInterceptor(
    private val context: Context
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cacheableRequest = if (request.method == "GET" && !hasNetwork(context)) {
            request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=$OFFLINE_MAX_STALE_SECONDS")
                .build()
        } else {
            request
        }
        return chain.proceed(cacheableRequest)
    }
}

private class GetResponseCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().method != "GET") {
            return response
        }
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$NETWORK_MAX_AGE_SECONDS")
            .build()
    }
}
