package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Track

@Entity(
    tableName = "cached_tracks",
    indices = [
        Index(value = ["driveFileId"], unique = true),
        Index(value = ["genre"]),
        Index(value = ["language"]),
        Index(value = ["updatedAt"])
    ]
)
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val driveFileId: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val duration: String,
    val durationSeconds: Int,
    val spotifyId: String?,
    val albumArt: String,
    val streamUrl: String,
    val language: String,
    val source: String,
    val requestedBy: String?,
    val lyrics: String?,
    val syncedLyrics: String?,
    val lyricsStatus: String,
    val timestamp: String?,
    val addedAt: String?,
    val updatedAt: String?,
    val cachedAt: Long
) {
    fun toTrack(
        isDownloaded: Boolean = false,
        localFilePath: String? = null
    ): Track {
        return Track(
            id = id,
            driveFileId = driveFileId,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            duration = duration,
            durationSeconds = durationSeconds,
            spotifyId = spotifyId,
            album_art = albumArt,
            albumArt = albumArt,
            streamUrl = streamUrl,
            language = language,
            source = source,
            requestedBy = requestedBy,
            lyrics = lyrics,
            syncedLyrics = syncedLyrics,
            lyricsStatus = lyricsStatus,
            timestamp = timestamp,
            addedAt = addedAt,
            updatedAt = updatedAt,
            isDownloaded = isDownloaded,
            localFilePath = localFilePath
        )
    }

    companion object {
        fun fromTrack(track: Track, cachedAt: Long = System.currentTimeMillis()): CachedTrackEntity {
            return CachedTrackEntity(
                id = track.id,
                driveFileId = track.driveFileId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                genre = track.genre,
                duration = track.duration,
                durationSeconds = track.durationSeconds,
                spotifyId = track.spotifyId,
                albumArt = track.albumArt,
                streamUrl = track.streamUrl,
                language = track.language,
                source = track.source,
                requestedBy = track.requestedBy,
                lyrics = track.lyrics,
                syncedLyrics = track.syncedLyrics,
                lyricsStatus = track.lyricsStatus,
                timestamp = track.timestamp,
                addedAt = track.addedAt,
                updatedAt = track.updatedAt,
                cachedAt = cachedAt
            )
        }
    }
}
