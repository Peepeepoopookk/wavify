package com.example.model

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DriveTrack(
    @param:Json(name = "id") val id: String? = null,
    @param:Json(name = "driveFileId") val driveFileId: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "artist") val artist: String? = null,
    @param:Json(name = "album") val album: String? = null,
    @param:Json(name = "genre") val genre: String? = null,
    @param:Json(name = "duration") val duration: String? = "--:--",
    @param:Json(name = "durationSeconds") val durationSeconds: Int? = null,
    @param:Json(name = "spotify_id") val spotify_id: String? = null,
    @param:Json(name = "album_art") val album_art: String? = null,
    @param:Json(name = "albumArt") val albumArt: String? = null,
    @param:Json(name = "language") val language: String? = null,
    @param:Json(name = "source") val source: String? = null,
    @param:Json(name = "requestedBy") val requestedBy: String? = null,
    @param:Json(name = "lyrics") val lyrics: String? = null,
    @param:Json(name = "syncedLyrics") val syncedLyrics: String? = null,
    @param:Json(name = "lyricsStatus") val lyricsStatus: String? = null,
    @param:Json(name = "timestamp") val timestamp: String? = null,
    @param:Json(name = "addedAt") val addedAt: String? = null,
    @param:Json(name = "updatedAt") val updatedAt: String? = null
) {
    fun toTrack(): Track {
        val playbackId = driveFileId ?: id.orEmpty()
        val resolvedGenre = genre
            ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
            ?: "Unknown"
        val resolvedLanguage = language
            ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
            ?: "Unknown"
        val resolvedAlbumArt = resolveAlbumArt(
            primary = albumArt,
            secondary = album_art,
            seed = "$playbackId-${title.orEmpty()}-${artist.orEmpty()}"
        )

        return Track(
            id = id ?: playbackId,
            driveFileId = playbackId,
            title = title ?: "Unknown Title",
            artist = artist ?: "Unknown Artist",
            album = album ?: "Unknown Album",
            genre = resolvedGenre,
            duration = duration ?: "--:--",
            durationSeconds = durationSeconds ?: 0,
            spotifyId = spotify_id,
            album_art = album_art,
            albumArt = resolvedAlbumArt,
            streamUrl = "${BuildConfig.WAVIFY_PROXY_BASE_URL}/stream/$playbackId",
            language = resolvedLanguage,
            source = source ?: "unknown",
            requestedBy = requestedBy,
            lyrics = lyrics,
            syncedLyrics = syncedLyrics,
            lyricsStatus = lyricsStatus ?: "ok",
            timestamp = timestamp,
            addedAt = addedAt,
            updatedAt = updatedAt,
            isDownloaded = false
        )
    }
}
