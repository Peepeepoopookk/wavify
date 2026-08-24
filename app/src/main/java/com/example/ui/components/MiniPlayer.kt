package com.example.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    viewModel: MainViewModel,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()

    val track = currentTrack ?: return
    val progress = if (playbackDuration > 0) playbackPosition.toFloat() / playbackDuration else 0f
    
    val cardShape = androidx.compose.runtime.remember { RoundedCornerShape(14.dp) }
    val imageShape = androidx.compose.runtime.remember { RoundedCornerShape(8.dp) }
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "miniPlayerShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "miniPlayerShimmerAlpha"
    )

    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val isNextTrackLoading by viewModel.isNextTrackLoading.collectAsStateWithLifecycle()

    // Pulse animation for "Next" button when preloading
    val nextPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNextTrackLoading) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "miniNextPulseScale"
    )

    val haptic = com.example.ui.util.rememberAppHapticFeedback()

    Card(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(cardShape)
                .clickable {
                    haptic.click()
                    onExpandClick()
                }
        ) {
            // Thin progress bar (2dp) at the very top edge of the mini player floating container
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art thumbnail - rounded corners 8dp with subtle drop shadow
                Box(contentAlignment = Alignment.Center) {
                    AlbumArtImage(
                        albumArt = track.albumArt,
                        fallbackSeed = track.id,
                        contentDescription = "Mini Player Cover",
                        modifier = Modifier
                            .size(42.dp)
                            .then(
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            state = rememberSharedContentState(key = "album_art_${track.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = BoundsTransform { _, _ ->
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            },
                                            clipInOverlayDuringTransition = OverlayClip(imageShape)
                                        )
                                    }
                                } else Modifier
                            )
                            .shadow(elevation = 1.dp, shape = imageShape)
                            .clip(imageShape)
                            .drawBehind {
                                drawRect(surfaceVariantColor.copy(alpha = shimmerAlpha))
                            },
                        requestSize = 128
                    )
                    
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Artist Column stacked - title bold 14sp, artist 12sp grey
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "track_info_${track.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = BoundsTransform { _, _ ->
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        }
                                    )
                                }
                            } else Modifier
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp, // Song title bold 14sp as requested
                        fontWeight = FontWeight.W700, // Bold 700 as requested
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.testTag("mini_player_title")
                    )
                    Text(
                        text = track.artist,
                        fontSize = 12.sp, // Artist 12sp as requested
                        fontWeight = FontWeight.W500, // Artist font weight 500 as requested
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // grey as requested
                        maxLines = 1,
                        modifier = Modifier.testTag("mini_player_artist")
                    )
                }

                // Action controls: Play/pause should be a solid black circle with white icon
                IconButton(
                    onClick = {
                        haptic.click()
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.onSurface, shape = CircleShape)
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        haptic.tick()
                        viewModel.playNextTrack()
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = nextPulseScale
                            scaleY = nextPulseScale
                        }
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
