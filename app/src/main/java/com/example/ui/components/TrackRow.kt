package com.example.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isActive: Boolean,
    isDownloading: Boolean,
    progress: Float,
    viewModel: MainViewModel,
    onTrackSelect: (Track) -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    currentAccentColor: Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    var showMenu by remember { mutableStateOf(false) }
    val rowShape = remember { RoundedCornerShape(16.dp) }
    val imageShape = remember { RoundedCornerShape(12.dp) }
    val shadowColor = remember { Color.Black.copy(alpha = 0.08f) }

    val haptic = com.example.ui.util.rememberAppHapticFeedback()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(elevation = 2.dp, shape = rowShape, spotColor = shadowColor, ambientColor = shadowColor)
            .clip(rowShape)
            .background(if (isActive) currentAccentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onTrackSelect(track) },
                onLongClick = {
                    haptic.heavyClick()
                    showMenu = true
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtImage(
                albumArt = track.albumArt,
                fallbackSeed = track.id,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(imageShape),
                requestSize = 128
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Bold,
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
            Text(track.duration, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = {
                        haptic.tick()
                        onDownloadClick()
                    }) {
                        Icon(
                            if (track.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            null,
                            tint = if (track.isDownloaded) currentAccentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        TrackContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            track = track,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    track: Track,
    viewModel: MainViewModel
) {
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play Next") },
            onClick = {
                viewModel.playNext(track)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Queue") },
            onClick = {
                viewModel.addToQueue(track)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            onClick = { showPlaylistPicker = true; onDismiss() }
        )
    }

    if (showPlaylistPicker) {
        ModalBottomSheet(onDismissRequest = { showPlaylistPicker = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add to Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(localPlaylists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.addTrackToPlaylist(playlist.id, track)
                                showPlaylistPicker = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name)
                        }
                    }
                }
            }
        }
    }
}
