package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AlbumArtImage
import com.example.model.Track
import com.example.ui.components.TappableArtistText
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.QueueItem
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onArtistClick: (String) -> Unit, // Add this
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val isRepeatEnabled by viewModel.isRepeatEnabled.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val queueItems by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val sleepTimerRemainingMillis by viewModel.sleepTimerRemainingMillis.collectAsStateWithLifecycle()

    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val isNextTrackLoading by viewModel.isNextTrackLoading.collectAsStateWithLifecycle()

    var isUserSeeking by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(0f) }
    val displayPosition = if (isUserSeeking) sliderPosition.toLong() else playbackPosition

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val track = currentTrack ?: return

    val isFavorite = favoriteTrackIds.contains(track.id)
    val nextTrack = queueItems.firstOrNull()?.track
    val isDownloading = downloadProgress.containsKey(track.id)
    val progress = downloadProgress[track.id] ?: 0f

    // Pulse animation for "Next" button when preloading
    val infiniteTransition = rememberInfiniteTransition(label = "nextPulse")
    val nextPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNextTrackLoading) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nextPulseScale"
    )
    val nextPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNextTrackLoading) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nextPulseAlpha"
    )

    // Format helper for millis to mm:ss
    val formatTime: (Long) -> String = { ms ->
        if (ms <= 0) {
            "--:--"
        } else {
            val totalSecs = ms / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            String.format("%01d:%02d", mins, secs)
        }
    }

    // Capture swipe-down gesture details to trigger collapse callback
    var swipeSumY by remember { mutableStateOf(0f) }
    val swipeState = rememberDraggableState { deltaY ->
        swipeSumY += deltaY
        if (swipeSumY > 200f) {
            onDismiss()
        }
    }

    val containerShape = remember { RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.background)
            .testTag("now_playing_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = swipeState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { swipeSumY = 0f },
                        onDragStopped = { swipeSumY = 0f }
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_chevron")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Collapse Player",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Listening now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large rounded album art - taking up ~45% height
            val albumArtShape = remember { RoundedCornerShape(24.dp) }
            val shadowAmbientColor = remember { Color.Black.copy(alpha = 0.1f) }
            val shadowSpotColor = remember(accentColor) { accentColor.copy(alpha = 0.25f) }
            Card(
                modifier = Modifier
                    .height((configuration.screenHeightDp * 0.45f).dp)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 8.dp,
                        shape = albumArtShape,
                        clip = false,
                        ambientColor = shadowAmbientColor,
                        spotColor = shadowSpotColor
                    ),
                shape = albumArtShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumArtImage(
                        albumArt = track.albumArt,
                        fallbackSeed = track.id,
                        contentDescription = "Cover Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        requestSize = 1024
                    )
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Bar & Timers
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(displayPosition),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = if (isUserSeeking) sliderPosition else playbackPosition.toFloat(),
                    onValueChange = { 
                        isUserSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderPosition.toLong())
                        isUserSeeking = false
                    },
                    valueRange = 0f..(playbackDuration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        thumbColor = accentColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .padding(horizontal = 8.dp)
                        .testTag("playback_seek_slider")
                )
                Text(
                    text = formatTime(playbackDuration),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song Title & Artist Centered
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("now_playing_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            TappableArtistText(
                artist = track.artist,
                onArtistClick = { 
                    onArtistClick(it)
                    onDismiss()
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Playback Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("shuffle_button").size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playPreviousTrack() },
                    modifier = Modifier.testTag("prev_button_large").size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("play_pause_button_large"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNextTrack() },
                    modifier = Modifier
                        .scale(nextPulseScale)
                        .alpha(nextPulseAlpha)
                        .testTag("next_button_large")
                        .size(48.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.testTag("repeat_button").size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Utility Bar (Favorite, Download, Share)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleFavorite(track.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite Toggle",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        IconButton(onClick = { if (!track.isDownloaded) viewModel.downloadTrack(track.id) }) {
                            Icon(
                                imageVector = if (track.isDownloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                                contentDescription = "Download State",
                                tint = if (track.isDownloaded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.startRadioFromTrack(track) }) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Start radio",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleepTimerRemainingMillis > 0L) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Sharing music: ${track.title}")
                            putExtra(Intent.EXTRA_TEXT, "Listen to '${track.title}' by ${track.artist} on Wavify!")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share track info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (sleepTimerRemainingMillis > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sleep timer: ${formatTimerRemaining(sleepTimerRemainingMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // "Next up" Section
            if (nextTrack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showQueue = true }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val nextTrackRowShape = remember { RoundedCornerShape(12.dp) }
                val nextTrackImageShape = remember { RoundedCornerShape(8.dp) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(nextTrackRowShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { viewModel.playNextTrack() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtImage(
                        albumArt = nextTrack.albumArt,
                        fallbackSeed = nextTrack.id,
                        contentDescription = "Next Track Cover",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(nextTrackImageShape),
                        contentScale = ContentScale.Crop,
                        requestSize = 128
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextTrack.artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = nextTrack.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Next Track",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Section
            LyricsSection(
                lyrics = track.lyrics,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .padding(bottom = 32.dp)
            )
        }

        if (showQueue) {
            PlaybackQueueSheet(
                currentTrack = track,
                queueItems = queueItems,
                accentColor = accentColor,
                onDismiss = { showQueue = false },
                onClearManualQueue = viewModel::clearManualQueue,
                onRemoveManualQueueItem = viewModel::removeManualQueueItem,
                onMoveManualQueueItem = viewModel::moveManualQueueItem,
                onPlayQueueItem = viewModel::playQueueItem
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                remainingMillis = sleepTimerRemainingMillis,
                onDismiss = { showSleepTimerDialog = false },
                onStart = { minutes ->
                    viewModel.startSleepTimer(minutes)
                    showSleepTimerDialog = false
                },
                onCancel = {
                    viewModel.cancelSleepTimer()
                    showSleepTimerDialog = false
                }
            )
        }
    }
}

private fun formatTimerRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SleepTimerDialog(
    remainingMillis: Long,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (remainingMillis > 0L) {
                    Text(
                        text = "Stops playback in ${formatTimerRemaining(remainingMillis)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                listOf(15, 30, 45, 60).forEach { minutes ->
                    FilledTonalButton(
                        onClick = { onStart(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$minutes minutes")
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMillis > 0L) {
                TextButton(onClick = onCancel) {
                    Text("Cancel timer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaybackQueueSheet(
    currentTrack: Track,
    queueItems: List<QueueItem>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onClearManualQueue: () -> Unit,
    onRemoveManualQueueItem: (Int) -> Unit,
    onMoveManualQueueItem: (Int, Int) -> Unit,
    onPlayQueueItem: (Int) -> Unit
) {
    val manualItems = queueItems.filter { it.isManual }
    val sourceItems = queueItems.filterNot { it.isManual }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Now playing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            QueueTrackRow(
                track = currentTrack,
                accentColor = accentColor,
                isCurrent = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (manualItems.isNotEmpty()) {
                    item(key = "manual-header") {
                        QueueSectionHeader(
                            title = "Next in queue",
                            actionText = "Clear",
                            onActionClick = onClearManualQueue
                        )
                    }
                    itemsIndexed(
                        manualItems,
                        key = { index, item -> "manual-$index-${item.track.id}" }
                    ) { index, item ->
                        QueueTrackRow(
                            track = item.track,
                            accentColor = accentColor,
                            isManual = true,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                onPlayQueueItem(index)
                                onDismiss()
                            },
                            onMoveUp = if (index > 0) {
                                { onMoveManualQueueItem(index, index - 1) }
                            } else {
                                null
                            },
                            onMoveDown = if (index < manualItems.lastIndex) {
                                { onMoveManualQueueItem(index, index + 1) }
                            } else {
                                null
                            },
                            onRemove = { onRemoveManualQueueItem(index) }
                        )
                    }
                }

                if (sourceItems.isNotEmpty()) {
                    item(key = "source-header") {
                        QueueSectionHeader(title = "Next from source")
                    }
                    itemsIndexed(
                        sourceItems,
                        key = { index, item -> "source-$index-${item.track.id}" }
                    ) { index, item ->
                        val queueIndex = manualItems.size + index
                        QueueTrackRow(
                            track = item.track,
                            accentColor = accentColor,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                onPlayQueueItem(queueIndex)
                                onDismiss()
                            }
                        )
                    }
                }

                if (queueItems.isEmpty()) {
                    item(key = "empty-queue") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No upcoming songs",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: Track,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isManual: Boolean = false,
    onClick: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val rowShape = remember { RoundedCornerShape(12.dp) }
    val imageShape = remember { RoundedCornerShape(8.dp) }
    val rowColor by animateColorAsState(
        targetValue = when {
            isCurrent -> accentColor.copy(alpha = 0.12f)
            isManual -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "queueRowColor"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowColor)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtImage(
            albumArt = track.albumArt,
            fallbackSeed = track.id,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(imageShape),
            contentScale = ContentScale.Crop,
            requestSize = 128
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isManual) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onRemove?.invoke() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsSection(
    lyrics: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val lyricsShape = remember { RoundedCornerShape(16.dp) }
    Card(
        modifier = modifier,
        shape = lyricsShape,
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (lyrics.isNullOrBlank()) "Lyrics not available" else lyrics,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}
