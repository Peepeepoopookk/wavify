package com.example

import android.os.Bundle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.ui.components.MiniPlayer
import com.example.ui.components.rememberDominantColor
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpotifyGreen
import com.example.viewmodel.MainViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.ArtistDetailScreen
import com.example.ui.screens.ImportScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.model.resolveAlbumArt
import com.example.viewmodel.ProfileViewModel
import com.example.viewmodel.EqualizerViewModel
import com.example.viewmodel.ImportViewModel
import kotlinx.coroutines.delay

const val ROUTE_HOME = "home"
const val ROUTE_SEARCH = "search"
const val ROUTE_LIBRARY = "library"
const val ROUTE_IMPORT = "import"
const val ROUTE_PROFILE = "profile"
const val ROUTE_EQUALIZER = "equalizer"
const val ROUTE_ARTIST_DETAIL = "artist_detail/{artistName}"
const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}/{isImported}"

private const val ROOT_TRANSITION_MS = 80
private const val SHEET_TRANSITION_MS = 220
private const val OPENING_SPLASH_MIN_MS = 1100L

private fun NavHostController.navigateTopLevel(route: String) {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == route || (route == ROUTE_LIBRARY && currentRoute?.startsWith(ROUTE_LIBRARY) == true)) {
        return
    }

    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val equalizerViewModel: EqualizerViewModel = viewModel()
            val importViewModel: ImportViewModel = viewModel()
            
            val currentTrack by mainViewModel.currentTrack.collectAsStateWithLifecycle()
            val userPrefs by profileViewModel.userPreferences.collectAsStateWithLifecycle()
            
            val isDarkTheme = when (userPrefs.theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            // Dynamics Accent Palette Color mapping on current track change!
            val dynamicAccentColor = rememberDominantColor(albumArtUrl = currentTrack?.albumArt)
            val appAccentColor = if (isDarkTheme) SpotifyGreen else dynamicAccentColor

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                accentColor = appAccentColor
            ) {
                MainAppScaffold(
                    viewModel = mainViewModel,
                    profileViewModel = profileViewModel,
                    equalizerViewModel = equalizerViewModel,
                    importViewModel = importViewModel,
                    accentColor = appAccentColor
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    viewModel: MainViewModel,
    profileViewModel: ProfileViewModel,
    equalizerViewModel: EqualizerViewModel,
    importViewModel: ImportViewModel,
    accentColor: Color
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route ?: "home"

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isInitialLibraryLoaded by viewModel.isInitialLibraryLoaded.collectAsStateWithLifecycle()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var showOpeningSplash by remember { mutableStateOf(true) }
    val openingSplashStartedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(isInitialLibraryLoaded) {
        if (isInitialLibraryLoaded) {
            val elapsed = System.currentTimeMillis() - openingSplashStartedAt
            delay((OPENING_SPLASH_MIN_MS - elapsed).coerceAtLeast(0L))
            showOpeningSplash = false
        } else {
            showOpeningSplash = true
        }
    }

    BackHandler(enabled = isNowPlayingExpanded) {
        isNowPlayingExpanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!showOpeningSplash) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    // Mini player overlays directly on top of the navbar
                    if (currentTrack != null) {
                        MiniPlayer(
                            viewModel = viewModel,
                            onExpandClick = { isNowPlayingExpanded = true }
                        )
                    }

                    // Bottom navigation bar (with minimalist Apple Music label indicators)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentDestination == "home",
                            onClick = {
                                navController.navigateTopLevel(ROUTE_HOME)
                            },
                            icon = { Icon(Icons.Default.Home, "Home", modifier = Modifier.size(28.dp)) },
                            label = { Text("Home", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )

                        NavigationBarItem(
                            selected = currentDestination == "search",
                            onClick = {
                                navController.navigateTopLevel(ROUTE_SEARCH)
                            },
                            icon = { Icon(Icons.Default.Search, "Search", modifier = Modifier.size(28.dp)) },
                            label = { Text("Search", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )

                        NavigationBarItem(
                            selected = currentDestination?.startsWith("library") == true,
                            onClick = {
                                navController.navigateTopLevel(ROUTE_LIBRARY)
                            },
                            icon = { Icon(Icons.Default.MusicNote, "Library", modifier = Modifier.size(28.dp)) },
                            label = { Text("Library", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent // Pure minimalist indicator
                            )
                        )

                        NavigationBarItem(
                            selected = currentDestination == "import",
                            onClick = {
                                navController.navigateTopLevel(ROUTE_IMPORT)
                            },
                            icon = { Icon(Icons.Default.Add, "Import", modifier = Modifier.size(28.dp)) },
                            label = { Text("Import", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentColor,
                                selectedTextColor = accentColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
            // Host navigation graphs
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enterTransition = {
                    fadeIn(animationSpec = tween(ROOT_TRANSITION_MS))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(ROOT_TRANSITION_MS))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(ROOT_TRANSITION_MS))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(ROOT_TRANSITION_MS))
                }
            ) {
                composable(
                    "home",
                    enterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) }
                ) {
                    HomeScreen(
                        viewModel = viewModel,
                        profileViewModel = profileViewModel,
                        onTrackSelect = { track, tracks ->
                            viewModel.setTrack(track, tracks)
                            isNowPlayingExpanded = true
                        },
                        onPlaylistClick = { playlistId, isImported ->
                            navController.navigate("playlist_detail/$playlistId/$isImported")
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        },
                        onGenreClick = { genre ->
                            val encodedGenre = android.net.Uri.encode(genre)
                            navController.navigate("genre_detail/$encodedGenre")
                        },
                        onProfileClick = {
                            navController.navigate("profile")
                        },
                        onNavigateToLibrary = { tab ->
                            navController.navigate("library?tab=$tab") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onImportClick = {
                            navController.navigate("import") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(
                    "search",
                    enterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) }
                ) {
                    SearchScreen(
                        viewModel = viewModel,
                        onTrackSelect = { track, tracks ->
                            viewModel.setTrack(track, tracks)
                            isNowPlayingExpanded = true
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        }
                    )
                }
                composable(
                    "library?tab={tab}",
                    arguments = listOf(androidx.navigation.navArgument("tab") { defaultValue = "songs" }),
                    enterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) }
                ) { backStackEntry ->
                    val tabArg = backStackEntry.arguments?.getString("tab") ?: "songs"
                    val initialTab = when(tabArg) {
                        "playlists" -> 1
                        "downloads" -> 2
                        else -> 0
                    }
                    LibraryScreen(
                        initialTab = initialTab,
                        viewModel = viewModel,
                        profileViewModel = profileViewModel,
                        onTrackSelect = { track ->
                            viewModel.setTrack(track)
                            isNowPlayingExpanded = true
                        },
                        onPlaylistClick = { playlistId, isImported ->
                            navController.navigate("playlist_detail/$playlistId/$isImported")
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        },
                        onProfileClick = {
                            navController.navigate("profile")
                        }
                    )
                }
                composable(
                    "import",
                    enterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(ROOT_TRANSITION_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(ROOT_TRANSITION_MS)) }
                ) {
                    ImportScreen(
                        viewModel = importViewModel,
                        mainViewModel = viewModel,
                        profileViewModel = profileViewModel,
                        onTrackSelect = { track ->
                            val playlist = importViewModel.importHistory.value.map { item: com.example.service.ImportHistoryItem ->
                                com.example.model.Track(
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
                            }
                            viewModel.setTrack(track, playlist)
                            isNowPlayingExpanded = true
                        },
                        onProfileClick = {
                            navController.navigate("profile")
                        }
                    )
                }
                composable(
                    route = ROUTE_ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                    ArtistDetailScreen(
                        artistName = artistName,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track, playlist ->
                            viewModel.setTrack(track, playlist)
                            isNowPlayingExpanded = true
                        },
                        onArtistClick = { tappedArtist ->
                            val encodedName = android.net.Uri.encode(tappedArtist)
                            navController.navigate("artist_detail/$encodedName")
                        }
                    )
                }
                composable(
                    route = "genre_detail/{genre}",
                    arguments = listOf(androidx.navigation.navArgument("genre") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val genre = backStackEntry.arguments?.getString("genre") ?: ""
                    com.example.ui.screens.GenreDetailScreen(
                        genre = genre,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track, tracks ->
                            viewModel.setTrack(track, tracks)
                            isNowPlayingExpanded = true
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        }
                    )
                }

                composable("profile") {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onEqualizerClick = {
                            navController.navigate(ROUTE_EQUALIZER)
                        }
                    )
                }
                composable(ROUTE_EQUALIZER) {
                    EqualizerScreen(
                        viewModel = equalizerViewModel,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(
                    route = ROUTE_PLAYLIST_DETAIL,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                        navArgument("isImported") { type = NavType.BoolType }
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    val isImported = backStackEntry.arguments?.getBoolean("isImported") ?: false
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        isImported = isImported,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track, tracks ->
                            viewModel.setTrack(track, tracks)
                            isNowPlayingExpanded = true
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        }
                    )
                }
            }
            }

            // Sliding sheet Now Playing Screen Overlay animation
            AnimatedVisibility(
                visible = isNowPlayingExpanded,
                enter = slideInVertically(
                    initialOffsetY = { height -> height },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(SHEET_TRANSITION_MS)),
                exit = slideOutVertically(
                    targetOffsetY = { height -> height },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(animationSpec = tween(SHEET_TRANSITION_MS))
            ) {
                NowPlayingScreen(
                    viewModel = viewModel,
                    onDismiss = { isNowPlayingExpanded = false },
                    onArtistClick = { artistName ->
                        val encodedName = android.net.Uri.encode(artistName)
                        navController.navigate("artist_detail/$encodedName")
                    },
                    accentColor = accentColor
                )
            }
        } else {
            OpeningMusicSplash(accentColor = accentColor)
        }
    }
}

@Composable
private fun OpeningMusicSplash(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "openingLogo")
    val logoScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlow"
    )
    val barOne by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "barOne")
    val barTwo by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "barTwo")
    val barThree by transition.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "barThree")
    val barFour by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(840, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "barFour")
    val bars = listOf(barOne, barTwo, barThree, barFour)
    val logoShape = remember { RoundedCornerShape(36.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        Color.Black
                    )
                )
            )
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        }
                        .background(accentColor.copy(alpha = glowAlpha), logoShape)
                )
                Box(
                    modifier = Modifier
                        .size(154.dp)
                        .clip(logoShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                            shape = logoShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_cropped_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEach { heightScale ->
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .fillMaxHeight(heightScale)
                            .clip(RoundedCornerShape(50))
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Wavify",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Getting your music ready",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
