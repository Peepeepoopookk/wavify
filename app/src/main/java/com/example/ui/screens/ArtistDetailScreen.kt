package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.TrackRow
import com.example.ui.components.WavifyPrimaryButton
import com.example.ui.components.WavifySecondaryButton
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val decodedName = remember(artistName) { android.net.Uri.decode(artistName) }
    var tracks by remember(decodedName) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(decodedName) {
        viewModel.getArtistTracks(decodedName).collect { tracks = it }
    }
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val artist = topArtists.find { it.name == decodedName }
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(decodedName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                ArtistHeader(
                    name = decodedName,
                    trackCount = artist?.track_count ?: tracks.size,
                    coverImage = artist?.cover_image ?: tracks.firstOrNull()?.albumArt,
                    onPlayAll = { if (tracks.isNotEmpty()) onTrackSelect(tracks.first(), tracks) },
                    onShuffle = {
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onTrackSelect(shuffled.first(), shuffled)
                        }
                    }
                )
            }

            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isActive = currentTrack?.id == track.id,
                    isDownloading = downloadProgress.containsKey(track.id),
                    progress = downloadProgress[track.id] ?: 0f,
                    viewModel = viewModel,
                    onTrackSelect = { onTrackSelect(track, tracks) },
                    onDownloadClick = { viewModel.downloadTrack(track.id) },
                    onArtistClick = { tappedArtist ->
                        if (!tappedArtist.equals(decodedName, ignoreCase = true)) {
                            onArtistClick(tappedArtist)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ArtistHeader(
    name: String,
    trackCount: Int,
    coverImage: String?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                albumArt = coverImage,
                fallbackSeed = name,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestSize = 540
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$trackCount tracks",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WavifyPrimaryButton(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All")
            }
            WavifySecondaryButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}
