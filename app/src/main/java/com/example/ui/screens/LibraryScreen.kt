@file:Suppress("DEPRECATION")
package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.rememberDominantColor
import com.example.ui.components.DownloadTrackRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.model.Track
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.combinedClickable
import com.example.data.local.LocalPlaylist
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.Add
import com.example.ui.components.TrackRow
import com.example.ui.components.TappableArtistText
import com.example.ui.components.TrackContextMenu
import com.example.ui.components.TrackListShimmer
import com.example.ui.components.WavifyPrimaryButton
import com.example.ui.components.WavifySecondaryButton
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

@Composable
fun AnimatedLanguageChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200, easing = EaseInOut),
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200, easing = EaseInOut),
        label = "chipText"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    profileViewModel: ProfileViewModel,
    onTrackSelect: (Track) -> Unit,
    onPlaylistClick: (String, Boolean) -> Unit,
    onArtistClick: (String) -> Unit, // Add this
    onProfileClick: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Songs", "Playlists", "Downloads")
    
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val accentColor = rememberDominantColor(currentTrack?.albumArt)
    
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val importedPlaylists by viewModel.importedPlaylists.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val userPrefs by profileViewModel.userPreferences.collectAsStateWithLifecycle()
    
    val softBackgroundColor = MaterialTheme.colorScheme.background

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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.loadImportedPlaylists()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(softBackgroundColor)
    ) {
        // App header / Custom Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Library",
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Segmented Control / Pill Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) accentColor else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> SongsSubTab(viewModel, onTrackSelect, onArtistClick, accentColor)
                1 -> PlaylistsSubTab(viewModel, localPlaylists, importedPlaylists, onPlaylistClick)
                2 -> DownloadsSubTab(viewModel, onTrackSelect, onArtistClick)
            }
        }
    }
}

@Composable
fun SongsSubTab(
    viewModel: MainViewModel,
    onTrackSelect: (Track) -> Unit,
    onArtistClick: (String) -> Unit,
    accentColor: Color
) {
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val activeLang by viewModel.activeLanguageFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    val languageOptions = listOf("All", "English", "Malayalam", "Tamil", "Hindi", "Kannada", "Telugu")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(languageOptions) { lang ->
                    AnimatedLanguageChip(
                        label = lang,
                        isSelected = activeLang == lang,
                        accentColor = accentColor,
                        onClick = { viewModel.setLanguageFilter(lang) }
                    )
                }
            }
            IconButton(onClick = { viewModel.toggleSearchMode(!isSearching) }) {
                Icon(if (isSearching) Icons.Default.Close else Icons.Default.Search, null)
            }
        }

        AnimatedVisibility(visible = isSearching) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Filter songs...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = CircleShape,
                singleLine = true,
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Default.Close, null) } }
            )
        }

        if (tracks.isEmpty() && isLoading) {
            TrackListShimmer(
                modifier = Modifier.fillMaxWidth(),
                rows = 8
            )
        } else if (tracks.isEmpty()) {
            EmptyState(isSearching, searchQuery, activeLang)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isActive = currentTrack?.id == track.id,
                        isDownloading = downloadProgress.containsKey(track.id),
                        progress = downloadProgress[track.id] ?: 0f,
                        viewModel = viewModel, // Pass it here
                        onTrackSelect = onTrackSelect,
                        onDownloadClick = { viewModel.downloadTrack(track.id) },
                        onArtistClick = onArtistClick,
                        currentAccentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsSubTab(
    viewModel: MainViewModel,
    localPlaylists: List<LocalPlaylist>,
    importedPlaylists: List<com.example.model.ImportedPlaylist>,
    onPlaylistClick: (String, Boolean) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WavifyPrimaryButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Playlist")
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(localPlaylists, key = { it.id }) { playlist ->
                PlaylistGridItem(
                    title = playlist.name,
                    label = "Local",
                    coverColor = playlist.coverColor,
                    onClick = { onPlaylistClick(playlist.id.toString(), false) }
                )
            }
            items(importedPlaylists, key = { it.id }) { playlist ->
                val context = LocalContext.current
                PlaylistGridItem(
                    title = playlist.name,
                    label = "Imported",
                    coverImage = playlist.bestCoverImage?.takeIf { it.isNotBlank() },
                    onClick = { onPlaylistClick(playlist.id, true) },
                    onSaveClick = {
                        viewModel.saveImportedPlaylistAsLocal(playlist) { success ->
                            if (success) {
                                Toast.makeText(context, "Saved to your library", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistGridItem(
    title: String,
    label: String,
    coverColor: String? = null,
    coverImage: String? = null,
    onClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (coverColor != null) Color(android.graphics.Color.parseColor(coverColor)) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (coverImage != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(coverImage)
                        .size(400, 400)
                        .crossfade(true)
                        .build(),
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp), tint = Color.White)
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (onSaveClick != null) {
                IconButton(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = "Save to Library", 
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSubTab(
    viewModel: MainViewModel,
    onTrackSelect: (Track) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.downloadSearchQuery.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()

    var sortOrder by remember { mutableStateOf("Recent") }

    var stats by remember { mutableStateOf(Triple(0, 0.0, 0.0)) }
    LaunchedEffect(downloadedTracks) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val totalSizeMb = downloadedTracks.sumOf { track ->
                val calculated = track.durationSeconds * 0.15
                calculated.coerceAtLeast(3.2)
            }
            
            val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            
            val newStats = Triple(downloadedTracks.size, totalSizeMb, freeGb)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                stats = newStats
            }
        }
    }

    val sortedTracks = remember(downloadedTracks, sortOrder) {
        when (sortOrder) {
            "A-Z" -> downloadedTracks.sortedBy { it.title }
            "Artist" -> downloadedTracks.sortedBy { it.artist }
            "Duration" -> downloadedTracks.sortedBy { it.durationSeconds }
            else -> downloadedTracks // Recent
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setDownloadSearchQuery(it) },
            placeholder = { Text("Search downloads...") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = CircleShape,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        if (downloadedTracks.isEmpty()) {
            EmptyDownloadsState()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WavifyPrimaryButton(onClick = { viewModel.setTrack(sortedTracks.first(), sortedTracks) }) {
                        Text("Play All")
                    }
                    WavifySecondaryButton(onClick = {
                        val shuffled = sortedTracks.shuffled()
                        viewModel.setTrack(shuffled.first(), shuffled) 
                    }) {
                        Text("Shuffle")
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("${stats.first} songs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(String.format("%.1f MB • %.1f GB free", stats.second, stats.third), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                val chips = listOf("Recent", "A-Z", "Artist", "Duration")
                items(chips, key = { it }) { chip ->
                    FilterChip(
                        selected = sortOrder == chip,
                        onClick = { sortOrder = chip },
                        label = { Text(chip) },
                        shape = CircleShape
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(sortedTracks, key = { it.id }) { track ->
                    DownloadTrackRow(
                        track = track,
                        isActive = currentTrack?.id == track.id,
                        onRowClick = { viewModel.setTrack(track, sortedTracks) },
                        onDeleteClick = { viewModel.deleteDownloadedTrack(track.id) },
                        onArtistClick = onArtistClick
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Download, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Downloads",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
fun EmptyState(isSearching: Boolean, query: String, lang: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (isSearching) "No Results for \"$query\"" else "No $lang Tracks",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}
