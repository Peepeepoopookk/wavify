package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImportedPlaylistsResponse(
    @param:Json(name = "playlists") val playlists: List<ImportedPlaylist> = emptyList(),
    @param:Json(name = "total_playlists") val total_playlists: Int = 0,
    @param:Json(name = "total_tracks") val total_tracks: Int = 0
)
