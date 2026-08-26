package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Track
import com.example.model.resolveAlbumArt
import com.example.ui.components.WavifyPrimaryButton
import com.example.ui.components.WavifySecondaryButton
import com.example.viewmodel.ImportViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    mainViewModel: MainViewModel,
    profileViewModel: ProfileViewModel,
    onTrackSelect: (Track) -> Unit,
    onProfileClick: () -> Unit
) {
    val songUrl by viewModel.songUrl.collectAsStateWithLifecycle()
    val songPreview by viewModel.songPreview.collectAsStateWithLifecycle()
    val songStatus by viewModel.songStatus.collectAsStateWithLifecycle()
    val isSongLoading by viewModel.isSongLoading.collectAsStateWithLifecycle()

    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val playlistPreview by viewModel.playlistPreview.collectAsStateWithLifecycle()
    val isPlaylistLoading by viewModel.isPlaylistLoading.collectAsStateWithLifecycle()
    val playlistOverallStatus by viewModel.playlistOverallStatus.collectAsStateWithLifecycle()

    val importHistory by viewModel.importHistory.collectAsStateWithLifecycle()
    val importStats by viewModel.importStats.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val userPrefs by profileViewModel.userPreferences.collectAsStateWithLifecycle()

    var profileImageExists by remember { mutableStateOf(false) }
    LaunchedEffect(userPrefs.profilePicturePath) {
        if (userPrefs.profilePicturePath.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val exists = java.io.File(userPrefs.profilePicturePath).exists()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    profileImageExists = exists
                }
            }
        } else {
            profileImageExists = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadImportHistoryAndStats() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Import",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(userPrefs.avatarColor))
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (userPrefs.profilePicturePath.isNotEmpty() && profileImageExists) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(userPrefs.profilePicturePath)
                                .size(96, 96)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = userPrefs.displayName.take(1).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), thickness = 0.5.dp)

        Box(modifier = Modifier.weight(1f).pullRefresh(pullRefreshState)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // SECTION A - Add Song
                item {
                    Text(
                        text = "Add Song",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = songUrl,
                        onValueChange = { viewModel.setSongUrl(it) },
                        placeholder = { Text("Spotify Track URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(com.example.ui.theme.AppCornerRadius),
                        singleLine = true,
                        trailingIcon = {
                            if (songUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSongUrl("") }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WavifyPrimaryButton(
                        onClick = { viewModel.addSong() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = songUrl.isNotEmpty() && !isSongLoading
                    ) {
                        if (isSongLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Import Song")
                        }
                    }

                    if (songStatus.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            if (songStatus == "Importing..." || songStatus == "Starting import...") {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else if (songStatus == "Added to your library!") {
                                Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = songStatus, style = MaterialTheme.typography.bodyMedium, color = if (songStatus.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    songPreview?.let { track ->
                        Spacer(modifier = Modifier.height(12.dp))
                        TrackPreviewCard(track)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // SECTION B - Import Playlist
                item {
                    Text(
                        text = "Import Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { viewModel.setPlaylistUrl(it) },
                        placeholder = { Text("Spotify Playlist URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(com.example.ui.theme.AppCornerRadius),
                        singleLine = true,
                        trailingIcon = {
                            if (playlistUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setPlaylistUrl("") }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WavifySecondaryButton(
                            onClick = { viewModel.previewPlaylist() },
                            modifier = Modifier.weight(1f),
                            enabled = playlistUrl.isNotEmpty() && !isPlaylistLoading
                        ) {
                            Text("Preview")
                        }
                        WavifyPrimaryButton(
                            onClick = { 
                                viewModel.startPlaylistImport(onSuccess = {
                                    mainViewModel.loadImportedPlaylists()
                                    }) 
                            },
                            modifier = Modifier.weight(1f),
                            enabled = playlistPreview != null && !isPlaylistLoading
                        ) {
                            Text("Start Import")
                        }
                    }

                    playlistPreview?.let { preview ->
                        Spacer(modifier = Modifier.height(12.dp))
                        PlaylistPreviewCard(preview)
                    }

                    if (playlistOverallStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playlistOverallStatus,
                            color = if (playlistOverallStatus.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // SECTION C - My Imports
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Imports",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    importStats?.let { stats ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Songs", stats.totalSongs.toString())
                            StatItem("Playlists", stats.totalPlaylists.toString())
                            if (stats.lastImportDate != null) {
                                StatItem("Recent", stats.lastImportDate)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (importHistory.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("You haven't imported any songs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(importHistory) { item ->
                        ImportedHistoryRow(item) {
                            val track = Track(
                                id = item.driveFileId ?: "",
                                title = item.title,
                                artist = item.artist,
                                album = "",
                                duration = item.duration ?: "0:00",
                                durationSeconds = 0,
                                albumArt = resolveAlbumArt(
                                    primary = item.albumArt,
                                    secondary = null,
                                    seed = "${item.driveFileId.orEmpty()}-${item.title}-${item.artist}"
                                ),
                                streamUrl = "",
                                driveFileId = item.driveFileId ?: "",
                                genre = "",
                                language = ""
                            )
                            onTrackSelect(track)
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TrackPreviewCard(track: Track) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.albumArt)
                    .size(160, 160)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(com.example.ui.theme.AppCornerRadius))
                    .border(com.example.ui.theme.OutlineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(com.example.ui.theme.AppCornerRadius)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_placeholder),
                error = painterResource(R.drawable.ic_placeholder)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun PlaylistPreviewCard(preview: com.example.service.PlaylistPreviewResponse) {
    Card(
        shape = RoundedCornerShape(com.example.ui.theme.AppCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(preview.name, style = MaterialTheme.typography.titleLarge)
            Text("${preview.totalTracks} tracks | ${preview.estimatedSize}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            preview.firstTracks.take(5).forEach { track ->
                Text("• ${track.title} - ${track.artist}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ImportedHistoryRow(item: com.example.service.ImportHistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(item.albumArt)
                .size(128, 128)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(com.example.ui.theme.AppCornerRadius))
                .border(com.example.ui.theme.OutlineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(com.example.ui.theme.AppCornerRadius)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_placeholder)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(modifier = Modifier.width(8.dp))
                val sourceLabel = if (item.source == "app_playlist") "From playlist" else "Added directly"
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(sourceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(item.addedAt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
