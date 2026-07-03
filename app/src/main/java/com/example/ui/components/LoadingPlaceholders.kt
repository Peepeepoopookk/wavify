package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun WavifyShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val transition = rememberInfiniteTransition(label = "wavifyShimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -360f,
        targetValue = 960f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavifyShimmerOffset"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(shimmerOffset, 0f),
                    end = Offset(shimmerOffset + 360f, 360f)
                )
            )
    )
}

@Composable
fun TrackListShimmer(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WavifyShimmerBox(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WavifyShimmerBox(modifier = Modifier.fillMaxWidth(0.72f).height(14.dp))
                    WavifyShimmerBox(modifier = Modifier.fillMaxWidth(0.46f).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun HomeShimmerContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WavifyShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(170.dp)
                        .height(22.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(2) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WavifyShimmerBox(
                                modifier = Modifier.size(140.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            WavifyShimmerBox(modifier = Modifier.width(118.dp).height(13.dp))
                            WavifyShimmerBox(modifier = Modifier.width(84.dp).height(11.dp))
                        }
                    }
                }
            }
        }
    }
}
