package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import com.example.ui.components.TappableArtistText
import com.example.ui.components.WavifyPrimaryButton
import com.example.ui.components.WavifySecondaryButton
import com.example.viewmodel.ImportedPlaylistDetailState
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    isImported: Boolean,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val playlistIdLong = playlistId.toLongOrNull()
    if (!isImported && playlistIdLong == null) {
        return
    }

    var tracks by remember(playlistId, isImported) { mutableStateOf<List<Track>>(emptyList()) }
    var importedDetailState by remember(playlistId, isImported) { mutableStateOf(ImportedPlaylistDetailState()) }
    LaunchedEffect(playlistId, isImported) {
        if (isImported) {
            viewModel.getImportedPlaylistDetails(playlistId).collect { state ->
                importedDetailState = state
                tracks = state.tracks
            }
        } else {
            if (playlistIdLong != null) {
                viewModel.getPlaylistTracks(playlistIdLong).collect { tracks = it }
            }
        }
    }
    
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val importedPlaylists by viewModel.importedPlaylists.collectAsStateWithLifecycle()
    val importedPlaylist = if (isImported) importedDetailState.playlist ?: importedPlaylists.find { it.id == playlistId } else null

    val playlistName = remember(playlistId, isImported, localPlaylists, importedPlaylist) {
        if (isImported) importedPlaylist?.name ?: "Imported Playlist" else localPlaylists.find { it.id.toString() == playlistId }?.name ?: "Playlist"
    }
    val localPlaylist = remember(localPlaylists, playlistId) {
        localPlaylists.find { it.id.toString() == playlistId }
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    if (showRenameDialog && localPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanName = renameInput.trim()
                        if (cleanName.isNotEmpty()) {
                            viewModel.renamePlaylist(localPlaylist, cleanName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (!isImported) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    renameInput = playlistName
                                    showRenameDialog = true
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { 
                                    localPlaylist?.let { viewModel.deletePlaylist(it) }
                                    showMenu = false
                                    onBackClick()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header with Play/Shuffle
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WavifyPrimaryButton(
                    onClick = { if (tracks.isNotEmpty()) onTrackSelect(tracks.first(), tracks) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play")
                }
                WavifySecondaryButton(
                    onClick = {
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onTrackSelect(shuffled.first(), shuffled)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }

            if (tracks.isEmpty()) {
                if (isImported && importedDetailState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (isImported && importedDetailState.errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = importedDetailState.errorMessage ?: "Failed to load playlist songs",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (isImported && importedDetailState.hasBackendTracks) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Playlist songs are still processing or unavailable.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tracks in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        PlaylistTrackRow(
                            track = track,
                            isImported = isImported,
                            onTrackSelect = { onTrackSelect(track, tracks) },
                            onRemove = { if (!isImported) viewModel.removeTrackFromPlaylist(playlistId.toLong(), track.driveFileId) },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackRow(
    track: Track,
    isImported: Boolean,
    onTrackSelect: () -> Unit,
    onRemove: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackSelect() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtImage(
            albumArt = track.albumArt,
            fallbackSeed = track.id,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(com.example.ui.theme.AppCornerRadius)).border(com.example.ui.theme.OutlineWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(com.example.ui.theme.AppCornerRadius)),
            contentScale = ContentScale.Crop,
            requestSize = 96
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            TappableArtistText(
                artist = track.artist,
                onArtistClick = onArtistClick
            )
        }
        if (!isImported) {
            var showTrackMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showTrackMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Track options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showTrackMenu, onDismissRequest = { showTrackMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            showTrackMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

