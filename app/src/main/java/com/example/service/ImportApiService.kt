package com.example.service

import com.example.model.DriveTrack
import com.example.model.Artist
import com.example.model.ImportedPlaylist
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.ResponseBody

@JsonClass(generateAdapter = true)
data class AddSongRequest(
    @param:Json(name = "spotify_url") val spotifyUrl: String,
    @param:Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class ImportedTrackResponse(
    val title: String,
    val artist: String,
    @param:Json(name = "album_art") val albumArt: String? = null,
    @param:Json(name = "drive_file_id") val driveFileId: String? = null,
    val duration: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistStartRequest(
    @param:Json(name = "spotify_url") val spotifyUrl: String,
    @param:Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class PlaylistImportResponse(
    val status: String,
    @param:Json(name = "playlist_id") val playlistId: String? = null
)

@JsonClass(generateAdapter = true)
data class ImportHistoryItem(
    val title: String,
    val artist: String,
    @param:Json(name = "album_art") val albumArt: String? = null,
    @param:Json(name = "added_at") val addedAt: String,
    val source: String,
    @param:Json(name = "drive_file_id") val driveFileId: String? = null,
    val duration: String? = null
)

@JsonClass(generateAdapter = true)
data class ImportStatsResponse(
    @param:Json(name = "total_songs") val totalSongs: Int,
    @param:Json(name = "total_playlists") val totalPlaylists: Int,
    @param:Json(name = "last_import_date") val lastImportDate: String? = null
)

// Legacy models, keep them to not break other parts unnecessarily
data class SongStatusResponse(val status: String, val track: DriveTrack? = null, val error: String? = null)
data class PlaylistPreviewRequest(val playlistUrl: String)
data class PlaylistPreviewResponse(
    val name: String,
    val totalTracks: Int,
    val estimatedSize: String,
    val firstTracks: List<DriveTrack>
)
data class PlaylistStatusResponse(
    val status: String,
    val importedCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val totalTracks: Int
)

interface ImportApiService {
    @POST("/api/app/song/add")
    suspend fun addSong(@Body request: AddSongRequest): ImportedTrackResponse

    @POST("/api/app/playlist/start")
    suspend fun startPlaylistImport(@Body request: PlaylistStartRequest): PlaylistImportResponse

    @GET("/api/app/my-imports")
    suspend fun getMyImports(@Query("device_id") deviceId: String): List<ImportHistoryItem>
    
    @GET("/api/app/import-stats")
    suspend fun getImportStats(@Query("device_id") deviceId: String): ImportStatsResponse

    @GET("/api/app/song/status")
    suspend fun getSongStatus(@Query("taskId") taskId: String): SongStatusResponse

    @POST("/api/app/playlist/preview")
    suspend fun previewPlaylist(@Body request: PlaylistPreviewRequest): PlaylistPreviewResponse

    @GET("/api/app/playlist/status")
    suspend fun getPlaylistStatus(
        @Query("playlistId") playlistId: String,
        @Query("deviceId") deviceId: String
    ): PlaylistStatusResponse

    @GET("/api/artists")
    suspend fun getTopArtists(): List<Artist>

    @GET("/api/artists/{artistName}")
    suspend fun getArtistTracks(@Path("artistName") artistName: String): List<DriveTrack>

    @GET("/api/app/playlists")
    suspend fun getAppPlaylistsRaw(
        @Query("device_id") deviceIdSnake: String,
        @Query("deviceId") deviceIdCamel: String
    ): ResponseBody

    @GET("/api/playlists")
    suspend fun getPlaylistsRaw(
        @Query("device_id") deviceIdSnake: String,
        @Query("deviceId") deviceIdCamel: String
    ): ResponseBody

    @GET("/api/playlists/{playlistId}")
    suspend fun getPlaylistDetails(@Path("playlistId") playlistId: String): ImportedPlaylist
}
