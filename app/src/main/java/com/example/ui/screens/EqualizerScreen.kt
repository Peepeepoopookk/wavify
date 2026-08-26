package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.EqualizerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBackClick: () -> Unit,
    viewModel: EqualizerViewModel = viewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val bands by viewModel.bands.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Animated Visualizer Bar
            VisualizerHeader(bands = bands, isEnabled = isEnabled)

            Spacer(modifier = Modifier.height(48.dp))

            // Equalizer Sliders Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bands.forEachIndexed { index, band ->
                    VerticalEqualizerSlider(
                        value = band.value,
                        onValueChange = { viewModel.updateBandValue(index, it) },
                        color = band.color,
                        label = band.frequency,
                        isEnabled = isEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun VisualizerHeader(bands: List<com.example.viewmodel.EqualizerBand>, isEnabled: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(com.example.ui.theme.AppCornerRadius))
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            val barCount = 20
            for (i in 0 until barCount) {
                // Find nearest band for this bar
                val bandIndex = (i.toFloat() / barCount * bands.size).toInt().coerceIn(0, bands.size - 1)
                val band = bands[bandIndex]
                
                val animationScale by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 300 + (i * 20),
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )

                val baseHeight = 10f + (band.value + 12f) * 3f
                val animatedHeight = if (isEnabled) baseHeight * animationScale else 5f
                
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(animatedHeight.dp)
                        .background(
                            brush = remember(band.color) {
                                Brush.verticalGradient(
                                    colors = listOf(band.color, band.color.copy(alpha = 0.3f))
                                )
                            },
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun VerticalEqualizerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    label: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (isEnabled) 1f else 0.3f
    val inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
    val thumbCenterColor = MaterialTheme.colorScheme.onPrimary
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // dB value display
        Text(
            text = "${value.roundToInt()} dB",
            color = color.copy(alpha = alpha),
            fontSize = 12.sp,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Slider track and thumb
        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect
            if (isEnabled) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(0.9f)
                        .background(
                            brush = remember(color) {
                                Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0f))
                                )
                            }
                        )
                        .blur(10.dp)
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newValue = (1f - (offset.y / size.height)) * 24f - 12f
                                onValueChange(newValue.coerceIn(-12f, 12f))
                            }
                        ) { change, _ ->
                            val newValue = (1f - (change.position.y / size.height)) * 24f - 12f
                            onValueChange(newValue.coerceIn(-12f, 12f))
                        }
                    }
            ) {
                val trackWidth = 4.dp.toPx()
                val thumbRadius = 12.dp.toPx()
                val height = size.height
                
                // Track
                drawLine(
                    color = inactiveTrackColor,
                    start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2, height),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )

                // Active Track (Glow)
                val thumbY = (1f - (value + 12f) / 24f) * height
                
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = androidx.compose.ui.geometry.Offset(size.width / 2, thumbY),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2, height),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )

                // Thumb with glow
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = thumbRadius,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, thumbY)
                )
                
                if (isEnabled) {
                    drawCircle(
                        color = thumbCenterColor,
                        radius = thumbRadius / 2,
                        center = androidx.compose.ui.geometry.Offset(size.width / 2, thumbY)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency Label
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

