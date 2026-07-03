package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImportedPlaylist(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "source_url") val source_url: String? = null,
    @param:Json(name = "cover_image") val cover_image: String? = null,
    @param:Json(name = "coverImage") val coverImageOriginal: String? = null,
    @param:Json(name = "cover_collage") val cover_collage: List<String> = emptyList(),
    @param:Json(name = "track_ids") val track_ids: List<String> = emptyList(),
    @param:Json(name = "total_tracks") val total_tracks: Int = 0,
    @param:Json(name = "trackCount") val trackCountOriginal: Int? = null,
    @param:Json(name = "track_count") val track_count: Int? = null,
    @param:Json(name = "created_at") val created_at: String? = null,
    @param:Json(name = "imported_via") val imported_via: String? = null,
    @param:Json(name = "requestedBy") val requestedBy: String? = null,
    @param:Json(name = "tracks") val tracks: List<DriveTrack> = emptyList()
) {
    val coverImage: String?
        get() = cover_image ?: coverImageOriginal

    val trackCount: Int
        get() = when {
            tracks.isNotEmpty() -> tracks.size
            total_tracks > 0 -> total_tracks
            track_ids.isNotEmpty() -> track_ids.size
            else -> trackCountOriginal ?: track_count ?: 0
        }

    val hasAnyTracks: Boolean
        get() = trackCount > 0

    val bestCoverImage: String?
        get() = coverImage
            ?: tracks.firstNotNullOfOrNull { it.albumArt?.takeIf(String::isNotBlank) }
            ?: tracks.firstNotNullOfOrNull { it.album_art?.takeIf(String::isNotBlank) }
}
