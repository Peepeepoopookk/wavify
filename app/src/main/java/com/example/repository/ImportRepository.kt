package com.example.repository

import android.content.Context
import com.example.model.Track
import com.example.model.Artist
import com.example.model.ImportedPlaylist
import com.example.service.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.HttpException
import android.util.Log
import com.example.BuildConfig

class ImportRepository(context: Context) {
    private companion object {
        const val TAG = "ImportRepository"
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = cachedOkHttpClient(
        context = context,
        builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
    )

    private val retrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.WAVIFY_PROXY_BASE_URL}/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(ImportApiService::class.java)

    suspend fun addSong(spotifyUrl: String, deviceId: String): Result<ImportedTrackResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.addSong(AddSongRequest(spotifyUrl, deviceId))
            Result.success(response)
        } catch (e: HttpException) {
            logFailure("addSong", e)
            Result.failure(e)
        } catch (e: Exception) {
            logFailure("addSong", e)
            Result.failure(e)
        }
    }

    suspend fun getSongStatus(taskId: String): Result<SongStatusResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getSongStatus(taskId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun previewPlaylist(playlistUrl: String): Result<PlaylistPreviewResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.previewPlaylist(PlaylistPreviewRequest(playlistUrl)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startPlaylistImport(spotifyUrl: String, deviceId: String): Result<PlaylistImportResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.startPlaylistImport(PlaylistStartRequest(spotifyUrl, deviceId))
            Result.success(response)
        } catch (e: HttpException) {
            logFailure("startPlaylistImport", e)
            Result.failure(e)
        } catch (e: Exception) {
            logFailure("startPlaylistImport", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistStatus(playlistId: String, deviceId: String): Result<PlaylistStatusResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getPlaylistStatus(playlistId, deviceId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyImports(deviceId: String): Result<List<ImportHistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMyImports(deviceId)
            Result.success(response)
        } catch (e: HttpException) {
            logFailure("getMyImports", e)
            Result.failure(e)
        } catch (e: Exception) {
            logFailure("getMyImports", e)
            Result.failure(e)
        }
    }

    suspend fun getImportStats(deviceId: String): Result<ImportStatsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getImportStats(deviceId)
            Result.success(response)
        } catch (e: HttpException) {
            logFailure("getImportStats", e)
            Result.failure(e)
        } catch (e: Exception) {
            logFailure("getImportStats", e)
            Result.failure(e)
        }
    }

    suspend fun getTopArtists(): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getTopArtists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistTracks(artistName: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val driveTracks = apiService.getArtistTracks(artistName)
            Result.success(driveTracks.map { it.toTrack() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylists(deviceId: String): Result<List<ImportedPlaylist>> = withContext(Dispatchers.IO) {
        try {
            val playlists = apiService.getPlaylists(deviceId)
            Result.success(playlists)
        } catch (e: Exception) {
            logFailure("getPlaylists", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistDetails(playlistId: String): Result<ImportedPlaylist> = withContext(Dispatchers.IO) {
        try {
            val playlist = apiService.getPlaylistDetails(playlistId)
            Result.success(playlist)
        } catch (e: Exception) {
            logFailure("getPlaylistDetails", e)
            Result.failure(e)
        }
    }

    private fun logFailure(operation: String, error: Exception) {
        if (!BuildConfig.DEBUG) return
        val details = if (error is HttpException) {
            "HTTP ${error.code()}"
        } else {
            error::class.java.simpleName
        }
        Log.w(TAG, "$operation failed: $details")
    }
}
