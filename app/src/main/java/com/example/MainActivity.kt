package com.example

import android.os.Bundle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.example.viewmodel.AppUpdateState
import com.example.ui.components.UpdateDialog
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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

private fun detailEnterTransition() = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) + fadeIn(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

private fun detailExitTransition() = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) + fadeOut(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

private fun detailPopEnterTransition() = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) + fadeIn(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

private fun detailPopExitTransition() = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) + fadeOut(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

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

@OptIn(ExperimentalSharedTransitionApi::class)
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

    val context = LocalContext.current
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is AppUpdateState.UpToDate -> {
                Toast.makeText(context, "You're on the latest version (v${state.version})", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdateDialog()
            }
            is AppUpdateState.Error -> {
                Toast.makeText(context, "Update check: ${state.message}", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdateDialog()
            }
            is AppUpdateState.Checking -> {
                Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    val predictiveBackProgress = remember { Animatable(0f) }

    LaunchedEffect(isNowPlayingExpanded) {
        if (isNowPlayingExpanded) {
            predictiveBackProgress.snapTo(0f)
        }
    }

    PredictiveBackHandler(enabled = isNowPlayingExpanded) { progressFlow ->
        var isCommitted = false
        try {
            progressFlow.collect { backEvent ->
                predictiveBackProgress.snapTo(backEvent.progress)
            }
            isCommitted = true
            isNowPlayingExpanded = false
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } finally {
            if (!isCommitted) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    predictiveBackProgress.snapTo(0f)
                }
            }
        }
    }

    SharedTransitionLayout {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Mini player overlays directly on top of the navbar
                            AnimatedVisibility(
                                visible = currentTrack != null && !isNowPlayingExpanded,
                                enter = fadeIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            ) {
                                MiniPlayer(
                                    viewModel = viewModel,
                                    onExpandClick = { isNowPlayingExpanded = true },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility
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
                                indicatorColor = Color.Transparent
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
                    .padding(innerPadding)
                    .then(with(this@SharedTransitionLayout) { Modifier.skipToLookaheadSize() }),
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
                        onTrackSelect = { track ->
                            viewModel.playEndless(track)
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
                        onTrackSelect = { track ->
                            viewModel.playEndless(track)
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
                            viewModel.playEndless(track)
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
                            viewModel.playEndless(track)
                            isNowPlayingExpanded = true
                        },
                        onProfileClick = {
                            navController.navigate("profile")
                        }
                    )
                }
                composable(
                    route = ROUTE_ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
                    enterTransition = { detailEnterTransition() },
                    exitTransition = { detailExitTransition() },
                    popEnterTransition = { detailPopEnterTransition() },
                    popExitTransition = { detailPopExitTransition() }
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                    ArtistDetailScreen(
                        artistName = artistName,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track ->
                            viewModel.playEndless(track)
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
                    arguments = listOf(androidx.navigation.navArgument("genre") { type = androidx.navigation.NavType.StringType }),
                    enterTransition = { detailEnterTransition() },
                    exitTransition = { detailExitTransition() },
                    popEnterTransition = { detailPopEnterTransition() },
                    popExitTransition = { detailPopExitTransition() }
                ) { backStackEntry ->
                    val genre = backStackEntry.arguments?.getString("genre") ?: ""
                    com.example.ui.screens.GenreDetailScreen(
                        genre = genre,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track ->
                            viewModel.playEndless(track)
                            isNowPlayingExpanded = true
                        },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        }
                    )
                }

                composable(
                    route = "profile",
                    enterTransition = { detailEnterTransition() },
                    exitTransition = { detailExitTransition() },
                    popEnterTransition = { detailPopEnterTransition() },
                    popExitTransition = { detailPopExitTransition() }
                ) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onEqualizerClick = {
                            navController.navigate(ROUTE_EQUALIZER)
                        },
                        onCheckForUpdates = {
                            viewModel.checkForUpdates(isUserInitiated = true, forceCheck = true)
                        },
                        isCheckingUpdates = updateState is AppUpdateState.Checking,
                        currentVersionName = viewModel.getCurrentAppVersionName()
                    )
                }
                composable(
                    route = ROUTE_EQUALIZER,
                    enterTransition = { detailEnterTransition() },
                    exitTransition = { detailExitTransition() },
                    popEnterTransition = { detailPopEnterTransition() },
                    popExitTransition = { detailPopExitTransition() }
                ) {
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
                    ),
                    enterTransition = { detailEnterTransition() },
                    exitTransition = { detailExitTransition() },
                    popEnterTransition = { detailPopEnterTransition() },
                    popExitTransition = { detailPopExitTransition() }
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    val isImported = backStackEntry.arguments?.getBoolean("isImported") ?: false
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        isImported = isImported,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onTrackSelect = { track, tracks ->
                            viewModel.playFromPlaylist(track, tracks)
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

            // Shared Element Now Playing Screen Overlay animation
            AnimatedVisibility(
                visible = isNowPlayingExpanded,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                val progress = predictiveBackProgress.value
                val sheetScale = 1f - (progress * 0.10f)
                val sheetCornerRadius = (progress * 32f).dp
                val sheetTranslationY = (progress * 48f).dp
                val sheetAlpha = 1f - (progress * 0.12f)
                val sheetElevation = (progress * 16f).dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = sheetScale
                            scaleY = sheetScale
                            translationY = sheetTranslationY.toPx()
                            alpha = sheetAlpha
                            clip = progress > 0f
                            shape = RoundedCornerShape(sheetCornerRadius)
                            shadowElevation = sheetElevation.toPx()
                        }
                ) {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        onDismiss = { isNowPlayingExpanded = false },
                        onArtistClick = { artistName ->
                            val encodedName = android.net.Uri.encode(artistName)
                            navController.navigate("artist_detail/$encodedName")
                        },
                        accentColor = accentColor,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedVisibility
                    )
                }
            }

            (updateState as? AppUpdateState.Available)?.let { availableState ->
                UpdateDialog(
                    releaseInfo = availableState.release,
                    currentVersion = availableState.currentVersion,
                    isDownloading = availableState.isDownloading,
                    downloadProgress = availableState.downloadProgress,
                    downloadedBytes = availableState.downloadedBytes,
                    totalBytes = availableState.totalBytes,
                    downloadedFile = availableState.downloadedFile,
                    errorMessage = availableState.error,
                    onUpdateClick = { viewModel.startUpdateDownload() },
                    onInstallClick = { file -> viewModel.installDownloadedApk(file) },
                    onDismiss = { viewModel.dismissUpdateDialog() }
                )
            }
        }
    }
}
