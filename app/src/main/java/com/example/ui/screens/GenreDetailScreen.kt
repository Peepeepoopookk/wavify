package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.TrackRow
import com.example.ui.components.WavifyPrimaryButton
import com.example.ui.components.WavifySecondaryButton
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.normalizeGenreName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: String,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onTrackSelect: (Track) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val decodedGenre = remember(genre) { android.net.Uri.decode(genre) }
    val allTracks by viewModel.tracksState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    
    val tracks = remember(allTracks, decodedGenre) {
        val selectedGenre = normalizeGenreName(decodedGenre)
        allTracks.filter { normalizeGenreName(it.genre) == selectedGenre }
    }
    val headerArt = remember(tracks) { tracks.firstOrNull()?.albumArt }
    val handleTrackSelect = remember(onTrackSelect) {
        { selectedTrack: Track -> onTrackSelect(selectedTrack) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(decodedGenre, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    // Header Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(com.example.ui.theme.AppCornerRadius)).border(com.example.ui.theme.OutlineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(com.example.ui.theme.AppCornerRadius))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AlbumArtImage(
                                albumArt = headerArt,
                                fallbackSeed = decodedGenre,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                requestSize = 540
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = decodedGenre,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${tracks.size} tracks",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            WavifyPrimaryButton(
                                onClick = { viewModel.setTrack(tracks.first(), tracks) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play", style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            WavifySecondaryButton(
                                onClick = { 
                                    val shuffled = tracks.shuffled()
                                    viewModel.setTrack(shuffled.first(), shuffled) 
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Shuffle", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                
                items(tracks, key = { it.id }) { track ->
                    val onDownload = remember(track.id) { { viewModel.downloadTrack(track.id) } }
                    TrackRow(
                        track = track,
                        isActive = currentTrack?.id == track.id,
                        isDownloading = downloadProgress.containsKey(track.id),
                        progress = downloadProgress[track.id] ?: 0f,
                        viewModel = viewModel,
                        onTrackSelect = handleTrackSelect,
                        onDownloadClick = onDownload,
                        onArtistClick = onArtistClick
                    )
                }
            }
        }
    }
}

