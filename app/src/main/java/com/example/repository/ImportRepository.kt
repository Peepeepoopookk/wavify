package com.example.repository

import android.content.Context
import com.example.model.Track
import com.example.model.Artist
import com.example.model.ImportedPlaylist
import com.example.model.ImportedPlaylistsResponse
import com.example.service.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
        const val PLAYLIST_CACHE_PREFS = "imported_playlist_cache"
        const val PLAYLIST_LIST_KEY = "playlist_list"
        const val PLAYLIST_DETAILS_KEY = "playlist_details"
        const val MAX_CACHED_PLAYLIST_DETAILS = 60
    }
    private val appContext = context.applicationContext
    private val playlistCachePrefs = appContext.getSharedPreferences(PLAYLIST_CACHE_PREFS, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val playlistListType = Types.newParameterizedType(List::class.java, ImportedPlaylist::class.java)
    private val playlistMapType = Types.newParameterizedType(Map::class.java, String::class.java, ImportedPlaylist::class.java)
    private val playlistListAdapter = moshi.adapter<List<ImportedPlaylist>>(playlistListType)
    private val playlistMapAdapter = moshi.adapter<Map<String, ImportedPlaylist>>(playlistMapType)

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
            val body = apiService.getAppPlaylistsRaw(deviceId, deviceId).string()
            val appPlaylists = parseImportedPlaylists(body)
            if (appPlaylists.any { it.hasAnyTracks }) {
                Result.success(appPlaylists)
            } else {
                val fallbackBody = apiService.getPlaylistsRaw(deviceId, deviceId).string()
                Result.success(parseImportedPlaylists(fallbackBody))
            }
        } catch (appError: Exception) {
            try {
                val fallbackBody = apiService.getPlaylistsRaw(deviceId, deviceId).string()
                Result.success(parseImportedPlaylists(fallbackBody))
            } catch (fallbackError: Exception) {
                logFailure("getPlaylists", fallbackError)
                Result.failure(fallbackError)
            }
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

    fun getCachedImportedPlaylists(): List<ImportedPlaylist> {
        val json = playlistCachePrefs.getString(PLAYLIST_LIST_KEY, null) ?: return emptyList()
        return runCatching { playlistListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    fun getCachedImportedPlaylistDetails(): Map<String, ImportedPlaylist> {
        val json = playlistCachePrefs.getString(PLAYLIST_DETAILS_KEY, null) ?: return emptyMap()
        return runCatching { playlistMapAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyMap())
    }

    fun cacheImportedPlaylists(playlists: List<ImportedPlaylist>) {
        writeCachedImportedPlaylists(playlists.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.hasAnyTracks })
    }

    fun cacheImportedPlaylistDetail(playlist: ImportedPlaylist) {
        if (playlist.id.isBlank() || playlist.name.isBlank() || !playlist.hasAnyTracks) return

        val details = getCachedImportedPlaylistDetails().toMutableMap()
        details.remove(playlist.id)
        details[playlist.id] = playlist
        while (details.size > MAX_CACHED_PLAYLIST_DETAILS) {
            details.remove(details.keys.first())
        }
        playlistCachePrefs.edit()
            .putString(PLAYLIST_DETAILS_KEY, playlistMapAdapter.toJson(details))
            .apply()

        val summaries = getCachedImportedPlaylists().toMutableList()
        val index = summaries.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            summaries[index] = playlist
        } else {
            summaries += playlist
        }
        writeCachedImportedPlaylists(summaries)
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

    private fun parseImportedPlaylists(json: String): List<ImportedPlaylist> {
        val responseAdapter = moshi.adapter(ImportedPlaylistsResponse::class.java)
        return runCatching { playlistListAdapter.fromJson(json) }.getOrNull()
            ?: runCatching { responseAdapter.fromJson(json)?.playlists }.getOrNull()
            ?: emptyList()
    }

    private fun writeCachedImportedPlaylists(playlists: List<ImportedPlaylist>) {
        playlistCachePrefs.edit()
            .putString(PLAYLIST_LIST_KEY, playlistListAdapter.toJson(playlists))
            .apply()
    }
}

fun clearImportedPlaylistCache(context: Context) {
    context.applicationContext
        .getSharedPreferences("imported_playlist_cache", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}
