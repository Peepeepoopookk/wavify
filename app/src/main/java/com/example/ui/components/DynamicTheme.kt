package com.example.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.ui.theme.AccentBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dominantColorCache = LruCache<String, Int>(64)

@Composable
fun rememberDominantColor(albumArtUrl: String?): Color {
    val context = LocalContext.current.applicationContext
    var dominantColor by remember(albumArtUrl) { mutableStateOf(AccentBlue) }

    LaunchedEffect(albumArtUrl) {
        val cacheKey = albumArtUrl?.takeIf { it.isNotBlank() }
        if (cacheKey == null) {
            dominantColor = AccentBlue
            return@LaunchedEffect
        }

        dominantColorCache.get(cacheKey)?.let { cachedColor ->
            dominantColor = Color(cachedColor)
            return@LaunchedEffect
        }

        try {
            val selectedColor = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(cacheKey)
                    .size(128, 128)
                    .allowHardware(false)
                    .build()

                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap
                    ?: return@withContext AccentBlue.toArgb()

                val palette = Palette.from(bitmap).generate()
                palette.getVibrantColor(
                    palette.getLightVibrantColor(
                        palette.getDominantColor(AccentBlue.toArgb())
                    )
                )
            }
            dominantColorCache.put(cacheKey, selectedColor)
            dominantColor = Color(selectedColor)
        } catch (e: Exception) {
            dominantColor = AccentBlue
        }
    }
    return dominantColor
}
