package com.example.service

import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@UnstableApi
class MusicPlaybackService : MediaSessionService() {
    private companion object {
        const val TAG = "WavifyPlaybackService"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val cacheLimitMb = runBlocking(Dispatchers.IO) {
            UserPreferencesRepository(applicationContext).userPreferencesFlow.first().musicCacheLimitMb
        }
        MusicCache.setMaxCacheSizeBytes(cacheLimitMb * 1024L * 1024L)

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        val cache = MusicCache.getInstance(this)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(okHttpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // minBufferMs
                180000, // maxBufferMs
                1000,  // bufferForPlaybackMs
                2500   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        exoPlayer?.setSeekParameters(SeekParameters.CLOSEST_SYNC)

        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return if (isTrustedController(controllerInfo)) {
            mediaSession
        } else {
            Log.w(TAG, "Rejected media controller from ${controllerInfo.packageName}")
            null
        }
    }

    private fun isTrustedController(controllerInfo: MediaSession.ControllerInfo): Boolean {
        return controllerInfo.uid == Process.myUid() ||
                controllerInfo.uid == Process.SYSTEM_UID ||
                controllerInfo.packageName == packageName
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removed; stopping playback service and removing notification")
        mediaSession?.player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
