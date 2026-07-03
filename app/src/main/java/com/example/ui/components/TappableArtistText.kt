package com.example.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Suppress("DEPRECATION")
@Composable
fun TappableArtistText(
    artist: String,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight = FontWeight.Medium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val artists = artist.split(",")
    
    val annotatedString = buildAnnotatedString {
        artists.forEachIndexed { index, name ->
            val trimmedName = name.trim()
            val start = length
            withStyle(style = SpanStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight
            )) {
                append(trimmedName)
            }
            val end = length
            addStringAnnotation(
                tag = "ARTIST",
                annotation = trimmedName,
                start = start,
                end = end
            )
            
            if (index < artists.size - 1) {
                withStyle(style = SpanStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight
                )) {
                    append(", ")
                }
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = MaterialTheme.typography.bodyMedium, // Base style to avoid default blue link color
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "ARTIST", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onArtistClick(annotation.item)
                }
        }
    )
}
