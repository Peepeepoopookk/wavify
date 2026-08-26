package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.TappableArtistText
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onTrackSelect: (Track) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var recentSearches by rememberSaveable { mutableStateOf(emptyList<String>()) }
    


    


    val genres = listOf("Malayalam", "Tamil", "Hindi", "English", "Indian")

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        val message = error
        if (!message.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Retry",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryFailedOperation()
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Songs, artists...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
            )

            if (searchQuery.isEmpty()) {
                SearchInitialState(
                    genres = genres,
                    recentSearches = recentSearches,
                    onSearchClick = { viewModel.setSearchQuery(it) }
                )
            } else if (filteredTracks.isEmpty()) {
                SearchEmptyState(searchQuery)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        SearchTrackRow(
                            track = track,
                            onClick = {
                                recentSearches = updatedRecentSearches(recentSearches, searchQuery)
                                onTrackSelect(track)
                            },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun SearchInitialState(
    genres: List<String>,
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Recent Searches", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        if (recentSearches.isEmpty()) {
            Text("Your played search terms will appear here", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches, key = { it }) { query ->
                    AssistChip(
                        onClick = { onSearchClick(query) },
                        label = { Text(query, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Browse Genres", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genres, key = { it }) { genre ->
                GenreCard(genre = genre, onClick = { onSearchClick(genre) })
            }
        }
    }
}

private fun updatedRecentSearches(current: List<String>, query: String): List<String> {
    val cleanQuery = query.trim()
    if (cleanQuery.length < 2) return current
    return (listOf(cleanQuery) + current.filterNot { it.equals(cleanQuery, ignoreCase = true) }).take(8)
}

@Composable
fun SearchEmptyState(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No songs found for '$query'",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GenreCard(genre: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.height(80.dp).clickable { onClick() },
        shape = RoundedCornerShape(com.example.ui.theme.AppCornerRadius), border = androidx.compose.foundation.BorderStroke(com.example.ui.theme.OutlineWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
            Text(genre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun SearchTrackRow(track: Track, onClick: () -> Unit, onArtistClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
        Text(track.duration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


