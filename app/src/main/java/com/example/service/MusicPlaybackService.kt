package com.example.service

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@UnstableApi
class MusicPlaybackService : MediaSessionService() {
    private companion object {
        const val TAG = "WavifyPlaybackService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            UserPreferencesRepository(applicationContext).userPreferencesFlow.collect { prefs ->
                MusicCache.setMaxCacheSizeBytes(prefs.musicCacheLimitMb * 1024L * 1024L)
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val rangeHeader = request.header("Range")
                Log.d("WavifyNetwork", "HTTP Req: ${request.method} ${request.url} | Range: $rangeHeader")
                val response = chain.proceed(request)
                Log.d("WavifyNetwork", "HTTP Resp: code=${response.code} for ${request.url} | Content-Range=${response.header("Content-Range")} | Content-Length=${response.header("Content-Length")} | Accept-Ranges=${response.header("Accept-Ranges")}")
                response
            }
            .build()

        val cache = MusicCache.getInstance(this)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val upstreamDataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val playbackDataSourceFactory = LocalAwareDataSourceFactory(
            localFactory = upstreamDataSourceFactory,
            cachedFactory = cacheDataSourceFactory
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // minBufferMs
                180000, // maxBufferMs
                1000,  // bufferForPlaybackMs
                2500   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory))
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
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
        stopPlaybackAndNotification()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopPlaybackAndNotification()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun stopPlaybackAndNotification() {
        exoPlayer?.run {
            playWhenReady = false
            pause()
            stop()
            clearMediaItems()
        }
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private class LocalAwareDataSourceFactory(
        private val localFactory: DataSource.Factory,
        private val cachedFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return LocalAwareDataSource(
                localDataSource = localFactory.createDataSource(),
                cachedDataSource = cachedFactory.createDataSource()
            )
        }
    }

    private class LocalAwareDataSource(
        private val localDataSource: DataSource,
        private val cachedDataSource: DataSource
    ) : DataSource {
        private var activeDataSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            localDataSource.addTransferListener(transferListener)
            cachedDataSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            Log.d("WavifyDataSource", "DataSource.open: uri=${dataSpec.uri}, position=${dataSpec.position}, length=${dataSpec.length}, isLocal=${dataSpec.uri.isLocalPlaybackUri()}")
            activeDataSource = if (dataSpec.uri.isLocalPlaybackUri()) {
                localDataSource
            } else {
                cachedDataSource
            }
            val result = activeDataSource?.open(dataSpec) ?: C.RESULT_END_OF_INPUT.toLong()
            Log.d("WavifyDataSource", "DataSource.open result: $result, activeDataSource=${activeDataSource?.javaClass?.simpleName}")
            return result
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        }

        override fun getUri(): Uri? {
            return activeDataSource?.uri
        }

        override fun getResponseHeaders(): Map<String, List<String>> {
            return activeDataSource?.responseHeaders.orEmpty()
        }

        override fun close() {
            activeDataSource?.close()
            activeDataSource = null
        }

        private fun Uri.isLocalPlaybackUri(): Boolean {
            return scheme == "file" || scheme == "content" || scheme == "android.resource"
        }
    }
}
