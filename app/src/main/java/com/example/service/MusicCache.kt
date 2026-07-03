package com.example.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object MusicCache {
    private const val DEFAULT_CACHE_BYTES = 500L * 1024L * 1024L

    private var instance: SimpleCache? = null
    private var maxCacheBytes: Long = DEFAULT_CACHE_BYTES

    @Synchronized
    fun setMaxCacheSizeBytes(maxBytes: Long) {
        maxCacheBytes = maxBytes.coerceAtLeast(50L * 1024L * 1024L)
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (instance == null) {
            val cacheDir = File(context.cacheDir, "music_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(maxCacheBytes)
            val databaseProvider = StandaloneDatabaseProvider(context)
            instance = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return instance!!
    }

    @Synchronized
    fun clear(context: Context) {
        instance?.release()
        instance = null
        File(context.cacheDir, "music_cache").deleteRecursively()
    }
}
