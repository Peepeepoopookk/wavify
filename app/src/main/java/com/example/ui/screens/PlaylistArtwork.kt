package com.example.ui.screens

import com.example.model.ImportedPlaylist
import com.example.model.resolveAlbumArt

private fun String?.usableArtworkUrl(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return normalized.takeUnless {
        it.equals("null", ignoreCase = true) ||
                it.equals("none", ignoreCase = true) ||
                it.equals("undefined", ignoreCase = true) ||
                it.equals("n/a", ignoreCase = true)
    }
}

private fun List<String>.stableFour(seed: String): List<String> {
    return distinct()
        .sortedBy { "$seed|$it".hashCode() }
        .take(4)
}

internal fun ImportedPlaylist.playlistMosaicUrls(): List<String> {
    val trackArtwork = tracks.mapNotNull { track ->
        track.albumArt.usableArtworkUrl()
            ?: track.album_art.usableArtworkUrl()
            ?: resolveAlbumArt(
                primary = track.albumArt,
                secondary = track.album_art,
                seed = "${track.driveFileId ?: track.id.orEmpty()}-${track.title.orEmpty()}-${track.artist.orEmpty()}"
            ).usableArtworkUrl()
    }
    val collageArtwork = cover_collage.mapNotNull { it.usableArtworkUrl() }
    val singleCover = bestCoverImage.usableArtworkUrl()

    return when {
        trackArtwork.isNotEmpty() -> trackArtwork.stableFour(id)
        collageArtwork.isNotEmpty() -> collageArtwork.stableFour(id)
        singleCover != null -> listOf(singleCover)
        else -> emptyList()
    }
}
