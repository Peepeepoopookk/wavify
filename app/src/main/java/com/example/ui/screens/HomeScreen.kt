package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.example.model.Track
import com.example.model.ImportedPlaylist
import com.example.model.Artist
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.HomeShimmerContent
import com.example.viewmodel.HomeSectionKind
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    profileViewModel: ProfileViewModel,
    onTrackSelect: (Track) -> Unit,
    onPlaylistClick: (String, Boolean) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onNavigateToLibrary: (String) -> Unit,
    onImportClick: () -> Unit = {}
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val importedPlaylists by viewModel.importedPlaylists.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracksState.collectAsStateWithLifecycle()
    val homeSections by viewModel.homeSections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val userPrefs by profileViewModel.userPreferences.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var compactTrackCards by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadImportedPlaylists()
    }

    val madeForYou = remember(allTracks) {
        allTracks
            .sortedBy { "${it.id}-${it.title}" }
            .take(4)
    }
    val recentPreview = remember(recentlyPlayed) { recentlyPlayed.take(12) }
    val downloadedPreview = remember(downloadedTracks) { downloadedTracks.take(8) }
    val localPlaylistPreview = remember(localPlaylists) { localPlaylists }
    val importedPlaylistPreview = remember(importedPlaylists) { importedPlaylists }
    val hasPlaylistPreview = importedPlaylistPreview.isNotEmpty() || localPlaylistPreview.isNotEmpty()
    val topArtistPreview = remember(topArtists) { topArtists.take(12) }
    val visibleHomeSections = remember(homeSections) {
        homeSections.take(14)
    }
    val playlistCoverUrls = remember(importedPlaylistPreview) {
        importedPlaylistPreview
            .flatMap { it.playlistMosaicUrls() }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()
            .take(4)
    }
    val preloadAlbumArtUrls = remember(
        recentPreview,
        topArtistPreview,
        madeForYou,
        playlistCoverUrls
    ) {
        buildList {
            addAll(recentPreview.map { it.albumArt }.take(4))
            addAll(playlistCoverUrls)
            addAll(topArtistPreview.mapNotNull { it.cover_image }.take(4))
            addAll(madeForYou.map { it.albumArt }.take(4))
        }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()
            .take(16)
    }

    LaunchedEffect(preloadAlbumArtUrls) {
        preloadAlbumArtUrls.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(220, 220)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build()
            )
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshTracks() }
    )

    val softBackgroundColor = MaterialTheme.colorScheme.background

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Good night"
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(greeting, fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(userPrefs.avatarColor)),
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
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(onClick = { compactTrackCards = !compactTrackCards }) {
                        Icon(
                            imageVector = if (compactTrackCards) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = if (compactTrackCards) "Use wide cards" else "Use compact cards",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = softBackgroundColor)
            )
        },
        containerColor = softBackgroundColor
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).pullRefresh(pullRefreshState)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (isLoading && allTracks.isEmpty()) {
                    item(key = "home-shimmer", contentType = "loading") {
                        HomeShimmerContent()
                    }
                }

                if (recentPreview.isNotEmpty()) {
                    item(key = "recently-played", contentType = "track-section") {
                        HomeSection(title = "Recently Played", onSeeAllClick = { onNavigateToLibrary("songs") }) {
                            HomeTrackRail(
                                tracks = recentPreview,
                                compactTrackCards = compactTrackCards,
                                contentTypePrefix = "recent",
                                onTrackClick = onTrackSelect
                            )
                        }
                    }
                }

                if (hasPlaylistPreview) {
                    item(key = "playlists", contentType = "playlist-section") {
                        HomeSection(title = "Playlists", onSeeAllClick = { onNavigateToLibrary("playlists") }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                                items(
                                    items = importedPlaylistPreview,
                                    key = { it.id },
                                    contentType = { "imported-playlist-card" }
                                ) { playlist ->
                                    ImportedPlaylistCard(
                                        playlist = playlist,
                                        onClick = { onPlaylistClick(playlist.id, true) }
                                    )
                                }
                                items(
                                    items = localPlaylistPreview,
                                    key = { it.id },
                                    contentType = { "local-playlist-card" }
                                ) { playlist ->
                                    PlaylistCard(
                                        title = playlist.name,
                                        coverColor = playlist.coverColor,
                                        onClick = { onPlaylistClick(playlist.id.toString(), false) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (topArtistPreview.isNotEmpty()) {
                    item(key = "top-artists", contentType = "artist-section") {
                        HomeSection(title = "Top Artists", onSeeAllClick = {}) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                                items(
                                    items = topArtistPreview,
                                    key = { it.name },
                                    contentType = { "artist-card" }
                                ) { artist ->
                                    ArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
                                }
                            }
                        }
                    }
                }

                if (downloadedPreview.isNotEmpty()) {
                    item(key = "downloaded-tracks", contentType = "track-section") {
                        HomeSection(title = "Available Offline", onSeeAllClick = { onNavigateToLibrary("downloads") }) {
                            HomeTrackRail(
                                tracks = downloadedPreview,
                                compactTrackCards = compactTrackCards,
                                contentTypePrefix = "downloaded",
                                onTrackClick = onTrackSelect
                            )
                        }
                    }
                }

                item(key = "made-for-you", contentType = "grid-section") {
                    HomeSection(title = "Made For You", onSeeAllClick = {}) {
                        MadeForYouSection(
                            madeForYouTracks = madeForYou,
                            onTrackSelect = onTrackSelect
                        )
                    }
                }

                visibleHomeSections.forEach { section ->
                    item(key = "${section.kind}_${section.filterValue}_${section.title}", contentType = "suggestion-section") {
                        val seeAllClick = when (section.kind) {
                            HomeSectionKind.GENRE -> ({ onGenreClick(section.filterValue) })
                            HomeSectionKind.LANGUAGE -> ({
                                viewModel.setLanguageFilter(section.filterValue)
                                onNavigateToLibrary("songs")
                            })
                            HomeSectionKind.COLLECTION -> ({ onNavigateToLibrary("songs") })
                        }
                        HomeSection(title = section.title, onSeeAllClick = seeAllClick) {
                            if (section.layoutType == com.example.viewmodel.LayoutType.GRID) {
                                val genreTracks = section.tracks
                                MadeForYouSection(
                                    madeForYouTracks = genreTracks.take(4),
                                    onTrackSelect = onTrackSelect
                                )
                            } else {
                                HomeTrackRail(
                                    tracks = section.tracks,
                                    compactTrackCards = compactTrackCards,
                                    contentTypePrefix = "genre-${section.genre}",
                                    onTrackClick = onTrackSelect
                                )
                            }
                        }
                    }
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
fun HomeTrackRail(
    tracks: List<Track>,
    compactTrackCards: Boolean,
    contentTypePrefix: String,
    onTrackClick: (Track) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = tracks,
            key = { it.id },
            contentType = { if (compactTrackCards) "$contentTypePrefix-square-track" else "$contentTypePrefix-wide-track" }
        ) { track ->
            if (compactTrackCards) {
                TrackCard(track = track, onClick = { onTrackClick(track) })
            } else {
                RecentlyPlayedCard(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
fun HomeSection(title: String, onSeeAllClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (onSeeAllClick != null) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onSeeAllClick() }
                )
            }
        }
        content()
    }
}

@Composable
fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        AlbumArtImage(
            albumArt = track.albumArt,
            fallbackSeed = track.id,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            requestSize = 256
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, fontSize = 12.sp)
    }
}

@Composable
fun PlaylistCard(title: String, coverColor: String, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(android.graphics.Color.parseColor(coverColor))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
    }
}

@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(modifier = Modifier.width(110.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                albumArt = artist.cover_image,
                fallbackSeed = artist.name,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestSize = 220
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(artist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ImportedPlaylistCard(playlist: ImportedPlaylist, onClick: () -> Unit) {
    val coverUrls = remember(
        playlist.id,
        playlist.cover_image,
        playlist.coverImageOriginal,
        playlist.cover_collage,
        playlist.tracks
    ) {
        playlist.playlistMosaicUrls()
    }
    
    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        PlaylistMosaicCover(urls = coverUrls, fallbackSeed = playlist.id)
        Spacer(modifier = Modifier.height(8.dp))
        Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
        Text("${playlist.trackCount} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun PlaylistMosaicCover(
    urls: List<String>,
    fallbackSeed: String,
    modifier: Modifier = Modifier.size(140.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (urls.isEmpty()) {
            AlbumArtImage(
                albumArt = null,
                fallbackSeed = fallbackSeed,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestSize = 300
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    MosaicItem(url = urls.getOrNull(0), fallbackSeed = "$fallbackSeed-mosaic-0", modifier = Modifier.weight(1f))
                    MosaicItem(url = urls.getOrNull(1), fallbackSeed = "$fallbackSeed-mosaic-1", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f)) {
                    MosaicItem(url = urls.getOrNull(2), fallbackSeed = "$fallbackSeed-mosaic-2", modifier = Modifier.weight(1f))
                    MosaicItem(url = urls.getOrNull(3), fallbackSeed = "$fallbackSeed-mosaic-3", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MosaicItem(url: String?, fallbackSeed: String, modifier: Modifier = Modifier) {
    AlbumArtImage(
        albumArt = url,
        fallbackSeed = fallbackSeed,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        requestSize = 160
    )
}

@Composable
fun RecentlyPlayedCard(track: Track, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtImage(
                albumArt = track.albumArt,
                fallbackSeed = track.id,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                requestSize = 180
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MadeForYouSection(madeForYouTracks: List<Track>, onTrackSelect: (Track) -> Unit) {
    if (madeForYouTracks.size < 4) return // Wait until loaded
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MixTile(track = madeForYouTracks[0], modifier = Modifier.weight(1f)) { onTrackSelect(madeForYouTracks[0]) }
            MixTile(track = madeForYouTracks[1], modifier = Modifier.weight(1f)) { onTrackSelect(madeForYouTracks[1]) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MixTile(track = madeForYouTracks[2], modifier = Modifier.weight(1f)) { onTrackSelect(madeForYouTracks[2]) }
            MixTile(track = madeForYouTracks[3], modifier = Modifier.weight(1f)) { onTrackSelect(madeForYouTracks[3]) }
        }
    }
}

@Composable
fun MixTile(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        AlbumArtImage(
            albumArt = track.albumArt,
            fallbackSeed = track.id,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            requestSize = 320
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 100f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ImportPlaylistPromoCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text(
                        text = "Bring your Spotify Playlists",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import and play your music seamlessly on Wavify.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Import", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

