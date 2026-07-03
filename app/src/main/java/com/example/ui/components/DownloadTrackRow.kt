package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import kotlin.math.roundToInt

@Composable
fun DownloadTrackRow(
    track: Track,
    isActive: Boolean,
    onRowClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val deleteButtonWidth = 65.dp // Reduced from 80dp
    val deleteButtonPx = with(density) { -deleteButtonWidth.toPx() }

    var offsetPositionX by remember { mutableStateOf(0f) }

    val draggableState = rememberDraggableState { deltaX ->
        val targetOffset = offsetPositionX + deltaX
        offsetPositionX = targetOffset.coerceIn(deleteButtonPx, 0f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.errorContainer) // Softer delete background
    ) {
        // Delete button revealed on swipe
        Box(
            modifier = Modifier
                .width(deleteButtonWidth)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .clickable {
                    offsetPositionX = 0f
                    onDeleteClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                Text(text = "Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scaleState by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )

        val rowBgColor by animateColorAsState(
            targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background,
            animationSpec = tween(300)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetPositionX.roundToInt(), 0) }
                .scale(scaleState)
                .background(rowBgColor)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        offsetPositionX = if (offsetPositionX < deleteButtonPx / 2f) deleteButtonPx else 0f
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current
                ) { onRowClick() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtImage(
                    albumArt = track.albumArt,
                    fallbackSeed = track.id,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    requestSize = 128
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TappableArtistText(
                        artist = track.artist,
                        onArtistClick = onArtistClick,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val fileMb = (track.durationSeconds * 0.15).coerceAtLeast(3.2)
                    Text(
                        text = String.format("%.1f MB", fileMb),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = track.duration,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 66.dp)
            )
        }
    }
}
