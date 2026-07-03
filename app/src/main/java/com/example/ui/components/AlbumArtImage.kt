package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.model.fallbackAlbumArtFor

private val fallbackAlbumArtDrawables = listOf(
    R.drawable.fallback_album_art_1,
    R.drawable.fallback_album_art_2,
    R.drawable.fallback_album_art_3,
    R.drawable.fallback_album_art_4,
    R.drawable.fallback_album_art_5
)

private fun fallbackDrawableFor(seed: String): Int {
    val index = seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % fallbackAlbumArtDrawables.size
    return fallbackAlbumArtDrawables[index]
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

@Composable
fun AlbumArtImage(
    albumArt: String?,
    fallbackSeed: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int = 256
) {
    val context = LocalContext.current
    val fallback = remember(fallbackSeed) { fallbackDrawableFor(fallbackSeed) }
    val data = remember(albumArt, fallbackSeed) {
        albumArt.usableAlbumArt() ?: fallbackAlbumArtFor(fallbackSeed)
    }
    val imageRequest = remember(context, data, requestSize) {
        ImageRequest.Builder(context)
            .data(data)
            .size(requestSize, requestSize)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        placeholder = painterResource(fallback),
        error = painterResource(fallback),
        fallback = painterResource(fallback),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
