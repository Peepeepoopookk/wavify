package com.example.service

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.BuildConfig
import com.example.model.Track
import com.example.repository.UserPreferencesRepository
import com.example.repository.isUnmeteredNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@UnstableApi
class AudioPrefetcher(
    private val context: Context
) {
    private companion object {
        const val PREFETCH_BYTES = 2L * 1024L * 1024L
    }

    private val appContext = context.applicationContext
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            chain.proceed(request)
        }
        .build()

    @OptIn(UnstableApi::class)
    suspend fun prefetch(track: Track) = withContext(Dispatchers.IO) {
        val allowCellular = UserPreferencesRepository(appContext)
            .userPreferencesFlow
            .first()
            .prefetchOnCellular
        if (!allowCellular && !isUnmeteredNetwork(appContext)) {
            return@withContext
        }

        val streamUrl = "${BuildConfig.WAVIFY_PROXY_BASE_URL}/stream/${track.driveFileId}"
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(streamUrl))
            .setPosition(0)
            .setLength(PREFETCH_BYTES)
            .build()
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(MusicCache.getInstance(appContext))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

        CacheWriter(cacheDataSource, dataSpec, null, null).cache()
    }
}
