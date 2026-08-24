package com.example.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.TreeSet

@UnstableApi
class DynamicLeastRecentlyUsedCacheEvictor(
    @Volatile private var maxBytes: Long
) : CacheEvictor, Comparator<CacheSpan> {
    private val leastRecentlyUsed = TreeSet<CacheSpan>(this)
    private var currentSize: Long = 0L

    override fun requiresCacheSpanTouches(): Boolean = true
    override fun onCacheInitialized() {}
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {}

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0L)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    @Synchronized
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    @Synchronized
    fun setMaxBytes(cache: Cache?, newMaxBytes: Long) {
        maxBytes = newMaxBytes.coerceAtLeast(50L * 1024L * 1024L)
        if (cache != null) {
            evictCache(cache, 0L)
        }
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }

    override fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
        val timestampDelta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
        if (timestampDelta != 0L) {
            return if (timestampDelta < 0L) -1 else 1
        }
        val keyComparison = lhs.key.compareTo(rhs.key)
        if (keyComparison != 0) {
            return keyComparison
        }
        val posDiff = lhs.position - rhs.position
        if (posDiff != 0L) {
            return if (posDiff < 0L) -1 else 1
        }
        val lenDiff = lhs.length - rhs.length
        return if (lenDiff == 0L) 0 else if (lenDiff < 0L) -1 else 1
    }
}

@UnstableApi
object MusicCache {
    private const val DEFAULT_CACHE_BYTES = 500L * 1024L * 1024L

    private var instance: SimpleCache? = null
    private val evictor = DynamicLeastRecentlyUsedCacheEvictor(DEFAULT_CACHE_BYTES)

    @Synchronized
    fun setMaxCacheSizeBytes(maxBytes: Long) {
        evictor.setMaxBytes(instance, maxBytes)
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (instance == null) {
            val cacheDir = File(context.cacheDir, "music_cache")
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
