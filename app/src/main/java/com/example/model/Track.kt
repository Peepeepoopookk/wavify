package com.example.model

import com.example.BuildConfig

private val fallbackAlbumArtUrls = listOf(
    "android.resource://${BuildConfig.APPLICATION_ID}/drawable/fallback_album_art_1",
    "android.resource://${BuildConfig.APPLICATION_ID}/drawable/fallback_album_art_2",
    "android.resource://${BuildConfig.APPLICATION_ID}/drawable/fallback_album_art_3",
    "android.resource://${BuildConfig.APPLICATION_ID}/drawable/fallback_album_art_4",
    "android.resource://${BuildConfig.APPLICATION_ID}/drawable/fallback_album_art_5"
)

fun fallbackAlbumArtFor(seed: String): String {
    val index = seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % fallbackAlbumArtUrls.size
    return fallbackAlbumArtUrls[index]
}

private fun String?.usableAlbumArt(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return normalized.takeUnless {
        it.equals("null", ignoreCase = true) ||
                it.equals("none", ignoreCase = true) ||
                it.equals("undefined", ignoreCase = true) ||
                it.equals("n/a", ignoreCase = true)
    }
}

fun resolveAlbumArt(primary: String?, secondary: String?, seed: String): String {
    return primary.usableAlbumArt()
        ?: secondary.usableAlbumArt()
        ?: fallbackAlbumArtFor(seed)
}

data class Track(
    val id: String,
    val driveFileId: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val duration: String,
    val durationSeconds: Int,
    val spotifyId: String? = null,
    val album_art: String? = null,
    val albumArt: String,
    val streamUrl: String,
    val language: String,
    val source: String = "unknown",
    val requestedBy: String? = null,
    val lyrics: String? = null,
    val syncedLyrics: String? = null,
    val lyricsStatus: String = "ok",
    val timestamp: String? = null,
    val addedAt: String? = null,
    val updatedAt: String? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)
