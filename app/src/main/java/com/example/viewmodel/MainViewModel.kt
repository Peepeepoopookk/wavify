package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.LruCache
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.isActive
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.BuildConfig
import com.example.model.Track
import com.example.model.Artist
import com.example.model.ImportedPlaylist
import com.example.repository.DriveRepository
import com.example.repository.ImportRepository
import com.example.repository.MusicRepositoryImpl
import com.example.repository.UserPreferencesRepository
import com.example.service.AudioPrefetcher
import com.example.service.MusicPlaybackService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.repository.MusicRepository
import com.example.data.local.LocalPlaylist
import com.example.data.local.LocalPlaylistTrack
import com.example.data.local.RecentlyPlayedTrack
import com.example.data.local.WavifyDatabase
import com.example.data.local.CachedTrackEntity
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.download.DownloadManager
import com.example.download.TrackDownloadWorker

enum class LayoutType {
    GRID, ROW
}

data class HomeSectionData(
    val title: String,
    val genre: String,
    val tracks: List<Track>,
    val layoutType: LayoutType
)

data class QueueItem(
    val track: Track,
    val isManual: Boolean
)

data class ImportedPlaylistDetailState(
    val isLoading: Boolean = true,
    val playlist: ImportedPlaylist? = null,
    val tracks: List<Track> = emptyList(),
    val errorMessage: String? = null
) {
    val hasBackendTracks: Boolean
        get() = playlist?.let { it.track_ids.isNotEmpty() || it.total_tracks > 0 || it.tracks.isNotEmpty() } == true
}

fun normalizeGenreName(genre: String): String {
    return genre.trim().replace(Regex("\\s+"), " ").lowercase()
}

fun displayGenreName(genre: String): String {
    val normalized = normalizeGenreName(genre)
    if (normalized.isBlank()) return ""
    return normalized.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val INITIAL_UPCOMING_QUEUE_SIZE = 20
        const val QUEUE_APPEND_SIZE = 20
        const val QUEUE_APPEND_THRESHOLD = 5
    }

    private val repository: MusicRepository = MusicRepositoryImpl()
    private val driveRepository = DriveRepository(application)
    private val importRepository = ImportRepository(application)
    private val userPrefsRepository = UserPreferencesRepository(application)
    private val playlistDao = WavifyDatabase.getDatabase(application).playlistDao()
    private val downloadedTrackDao = WavifyDatabase.getDatabase(application).downloadedTrackDao()
    private val cachedTrackDao = WavifyDatabase.getDatabase(application).cachedTrackDao()
    private val downloadManager = DownloadManager(application)
    private val workManager = WorkManager.getInstance(application)
    private val handledDownloadWorkIds = mutableSetOf<java.util.UUID>()
    @OptIn(UnstableApi::class)
    private val audioPrefetcher = AudioPrefetcher(application)
    
    private val _homeSections = kotlinx.coroutines.flow.MutableStateFlow<List<HomeSectionData>>(emptyList())
    val homeSections: kotlinx.coroutines.flow.StateFlow<List<HomeSectionData>> = _homeSections
    
    // Core states
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _offlineModeEnabled = MutableStateFlow(false)
    val offlineModeEnabled: StateFlow<Boolean> = _offlineModeEnabled.asStateFlow()
    val tracksState: StateFlow<List<Track>> = combine(
        _tracks,
        _offlineModeEnabled
    ) { tracks, offlineMode ->
        tracks.visibleForOfflineMode(offlineMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isInitialLibraryLoaded = MutableStateFlow(false)
    val isInitialLibraryLoaded: StateFlow<Boolean> = _isInitialLibraryLoaded.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _isRepeatEnabled = MutableStateFlow(false)
    val isRepeatEnabled: StateFlow<Boolean> = _isRepeatEnabled.asStateFlow()

    private val _sleepTimerRemainingMillis = MutableStateFlow(0L)
    val sleepTimerRemainingMillis: StateFlow<Long> = _sleepTimerRemainingMillis.asStateFlow()

    // Filter and search states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeLanguageFilter = MutableStateFlow("All")
    val activeLanguageFilter: StateFlow<String> = _activeLanguageFilter.asStateFlow()

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    // Favorites and downloads
    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    // Tracks downloading progress
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    // Pull to Refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Local Playlists and History
    val localPlaylists: StateFlow<List<LocalPlaylist>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = combine(
        _tracks,
        playlistDao.getRecentlyPlayed(),
        _offlineModeEnabled
    ) { tracks, history, offlineMode ->
        val trackByDriveFileId = tracks.associateBy { it.driveFileId }
        history.mapNotNull { h -> trackByDriveFileId[h.driveFileId] }
            .visibleForOfflineMode(offlineMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importedPlaylists = MutableStateFlow<List<ImportedPlaylist>>(emptyList())
    val importedPlaylists: StateFlow<List<ImportedPlaylist>> = _importedPlaylists.asStateFlow()

    private val _topArtists = MutableStateFlow<List<Artist>>(emptyList())
    val topArtists: StateFlow<List<Artist>> = _topArtists.asStateFlow()

    private val _isNextTrackLoading = MutableStateFlow(false)
    val isNextTrackLoading: StateFlow<Boolean> = _isNextTrackLoading.asStateFlow()

    private val _manualQueue = MutableStateFlow<List<Track>>(emptyList())
    private val _playbackQueue = MutableStateFlow<List<QueueItem>>(emptyList())
    val playbackQueue: StateFlow<List<QueueItem>> = _playbackQueue.asStateFlow()

    private var currentQueueIds: List<String> = emptyList()
    private var originalPlaybackSource: List<Track> = emptyList()
    private var currentPlaybackSource: List<Track> = emptyList()
    private var currentPlaybackSourceIndex: Int = -1
    private var isCurrentTrackFromManualQueue = false
    private var loadedQueueEndExclusive: Int = 0
    private var hasUserInitiatedPlayback = false

    // List of tracks after applying search and filters
    val filteredTracks: StateFlow<List<Track>> = combine(
        _tracks,
        _searchQuery,
        _activeLanguageFilter,
        _offlineModeEnabled
    ) { trackList, query, lang, offlineMode ->
        trackList.visibleForOfflineMode(offlineMode).filter { track ->
            val matchesSearch = query.isEmpty() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true)
            val matchesLang = lang == "All" || track.language.equals(lang, ignoreCase = true)
            matchesSearch && matchesLang
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of downloaded tracks
    val downloadedTracks: StateFlow<List<Track>> = combine(
        _tracks,
        _downloadSearchQuery
    ) { list, query ->
        list.filter { it.isDownloaded }.filter { track ->
            query.isEmpty() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchMode(enabled: Boolean) {
        _isSearching.value = enabled
        if (!enabled) {
            _searchQuery.value = ""
        }
    }

    fun setLanguageFilter(language: String) {
        _activeLanguageFilter.value = language
        viewModelScope.launch {
            userPrefsRepository.updateLanguageFilter(language)
        }
    }

    // MediaController Instance
    private var browserFuture: ListenableFuture<MediaController>? = null
    private val player: Player? get() = if (browserFuture?.isDone == true) browserFuture?.get() else null
    private var playbackTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var audioPrefetchJob: Job? = null
    private var lastPrefetchedTrackId: String? = null

    private fun List<Track>.visibleForOfflineMode(offlineMode: Boolean): List<Track> {
        return if (offlineMode) filter { it.isDownloaded } else this
    }

    init {
        observeCachedTracks()
        observeDownloadWork()

        viewModelScope.launch(Dispatchers.IO) {
            val downloadedTracks = downloadedTrackDao.getAllSync()
            downloadedTracks.forEach { track ->
                val file = File(track.localFilePath)
                if (!file.exists()) {
                    downloadedTrackDao.delete(track.driveFileId)
                }
            }
        }
        
        // Initialize persisted filters and decide whether network refreshes are allowed.
        viewModelScope.launch {
            val initialPrefs = userPrefsRepository.userPreferencesFlow.first()
            _activeLanguageFilter.value = initialPrefs.languageFilter
            _offlineModeEnabled.value = initialPrefs.offlineMode
            if (initialPrefs.offlineMode) {
                _importedPlaylists.value = emptyList()
                _topArtists.value = emptyList()
                _isInitialLibraryLoaded.value = true
            } else {
                loadTracks()
                loadImportedPlaylists()
                loadTopArtists()
            }
        }

        viewModelScope.launch {
            userPrefsRepository.userPreferencesFlow.collectLatest { prefs ->
                _activeLanguageFilter.value = prefs.languageFilter
                val wasOffline = _offlineModeEnabled.value
                _offlineModeEnabled.value = prefs.offlineMode
                if (prefs.offlineMode) {
                    _importedPlaylists.value = emptyList()
                    _topArtists.value = emptyList()
                } else if (wasOffline) {
                    loadTracks()
                    loadImportedPlaylists()
                    loadTopArtists()
                }
            }
        }
        
        // Auto-refresh playlists
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(30_000)
                if (!_offlineModeEnabled.value) {
                    loadImportedPlaylists()
                }
            }
        }

        // Initialize MediaController
        try {
            val sessionToken = SessionToken(application, ComponentName(application, MusicPlaybackService::class.java))
            browserFuture = MediaController.Builder(application, sessionToken).buildAsync()
            browserFuture?.addListener({
                val controller = browserFuture?.get() ?: return@addListener
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        _isBuffering.value = state == Player.STATE_BUFFERING
                        
                        if (state == Player.STATE_READY) {
                            val duration = controller.duration
                            if (duration > 0 && duration != C.TIME_UNSET) {
                                _playbackDuration.value = duration
                            }
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            val track = _tracks.value.find { it.id == item.mediaId }
                            if (track != null) {
                                _currentTrack.value = track
                                val queuedManualTracks = _manualQueue.value
                                val isManualTransition = queuedManualTracks.firstOrNull()?.id == track.id
                                isCurrentTrackFromManualQueue = isManualTransition
                                if (isManualTransition) {
                                    _manualQueue.value = queuedManualTracks.drop(1)
                                }
                                if (!isManualTransition) {
                                    val sourceIndex = currentPlaybackSource.indexOfFirst { it.id == track.id }
                                    if (sourceIndex >= 0) {
                                        currentPlaybackSourceIndex = sourceIndex
                                    }
                                }
                                _playbackPosition.value = 0L
                                _playbackDuration.value = 0L
                                syncPlaybackQueueState()
                                appendMoreQueueItemsIfNeeded(controller)
                                // Save to history
                                if (hasUserInitiatedPlayback) {
                                    viewModelScope.launch {
                                        playlistDao.insertHistory(RecentlyPlayedTrack(track.driveFileId))
                                    }
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("WavifyStream", "Playback Error: ${error.errorCodeName} (${error.errorCode})")
                    }
                })
                
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e("WavifyViewModel", "Failed to connect to MediaSessionService", e)
        }

        startPlaybackTicker()
    }

    private fun observeCachedTracks() {
        viewModelScope.launch {
            combine(
                cachedTrackDao.getAllTracks(),
                downloadedTrackDao.getAll(),
                _offlineModeEnabled
            ) { cachedTracks, downloadedTracks, offlineMode ->
                val downloadedByDriveFileId = downloadedTracks.associateBy { it.driveFileId }
                val allTracks = cachedTracks.map { cachedTrack ->
                    val download = downloadedByDriveFileId[cachedTrack.driveFileId]
                    cachedTrack.toTrack(
                        isDownloaded = download != null,
                        localFilePath = download?.localFilePath
                    )
                }
                allTracks to allTracks.visibleForOfflineMode(offlineMode)
            }.collectLatest { (allTracks, visibleTracks) ->
                _tracks.value = allTracks
                generateHomeSections(visibleTracks)
                if (allTracks.isNotEmpty()) {
                    _isInitialLibraryLoaded.value = true
                }
            }
        }
    }

    private fun generateHomeSections(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            _homeSections.value = emptyList()
            return
        }

        val genreGroups = tracks
            .groupBy { normalizeGenreName(it.genre) }
            .filterKeys { it.isNotBlank() && it != "unknown" }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, List<Track>>> { (_, genreTracks) -> genreTracks.size }
                    .thenBy { (genreKey, _) -> displayGenreName(genreKey) }
            )
            .take(6)

        val newSections = genreGroups.mapIndexed { index, (genreKey, genreTracksForKey) ->
            val genre = displayGenreName(genreKey)
            val genreTracks = genreTracksForKey
                .sortedWith(
                    compareByDescending<Track> { it.addedAt ?: it.updatedAt ?: it.timestamp ?: "" }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.artist.lowercase() }
                        .thenBy { it.id }
                )
                .take(12)
            val layoutType = if (index % 2 == 0) LayoutType.ROW else LayoutType.GRID
            val titles = listOf("Trending in $genre", "Best of $genre", "$genre Hits", "Explore $genre")
            HomeSectionData(
                title = titles[index % titles.size],
                genre = genre,
                tracks = genreTracks,
                layoutType = layoutType
            )
        }.filter { it.tracks.isNotEmpty() }
        
        _homeSections.value = newSections
    }

    fun loadTracks() {
        viewModelScope.launch {
            if (_offlineModeEnabled.value) {
                _isLoading.value = false
                _isInitialLibraryLoaded.value = true
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            driveRepository.fetchTracks()
                .onSuccess { list ->
                    withContext(Dispatchers.IO) {
                        cachedTrackDao.replaceAll(list.map { CachedTrackEntity.fromTrack(it) })
                    }
                    _isLoading.value = false
                    _isInitialLibraryLoaded.value = true
                }
                .onFailure { exception ->
                    _error.value = "Failed to load tracks: ${exception.message}"
                    _isLoading.value = false
                    _isInitialLibraryLoaded.value = true
                    Log.e("WavifyViewModel", "Error fetching tracks", exception)
                }
        }
    }

    fun loadImportedPlaylists() {
        viewModelScope.launch {
            if (_offlineModeEnabled.value) {
                _importedPlaylists.value = emptyList()
                return@launch
            }
            val deviceId = userPrefsRepository.ensureDeviceId()
            importRepository.getPlaylists(deviceId)
                .onSuccess { playlists ->
                    _importedPlaylists.value = playlists
                        .filter { it.hasAnyTracks || it.total_tracks > 0 || it.track_ids.isNotEmpty() || it.tracks.isNotEmpty() }
                        .map { playlist -> _playlistDetailsCache.get(playlist.id) ?: playlist }
                }
                .onFailure { e ->
                    Log.e("WavifyViewModel", "Failed to load playlists", e)
                }
        }
    }

    fun loadTopArtists() {
        viewModelScope.launch {
            if (_offlineModeEnabled.value) {
                _topArtists.value = emptyList()
                return@launch
            }
            importRepository.getTopArtists()
                .onSuccess { artists ->
                    _topArtists.value = artists.sortedByDescending { it.track_count }.take(10)
                }
                .onFailure { e ->
                    Log.e("WavifyViewModel", "Failed to load top artists", e)
                }
        }
    }

    fun getArtistTracks(artistName: String): Flow<List<Track>> {
        return flow {
            if (_offlineModeEnabled.value) {
                emit(_tracks.value.filter { it.isDownloaded && it.artist.equals(artistName, ignoreCase = true) })
                return@flow
            }
            importRepository.getArtistTracks(artistName)
                .onSuccess { emit(it) }
                .onFailure { Log.e("WavifyViewModel", "Failed to fetch artist tracks", it) }
        }
    }

    private val _playlistTracksCache = LruCache<String, List<Track>>(50)
    private val _playlistDetailsCache = LruCache<String, ImportedPlaylist>(50)

    private fun cacheImportedPlaylistDetails(playlist: ImportedPlaylist) {
        _playlistDetailsCache.put(playlist.id, playlist)
        _playlistTracksCache.put(playlist.id, playlist.tracks.map { it.toTrack() })

        val currentPlaylists = _importedPlaylists.value.toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            currentPlaylists[index] = playlist
            _importedPlaylists.value = currentPlaylists
        } else if (playlist.hasAnyTracks) {
            _importedPlaylists.value = currentPlaylists + playlist
        }
    }

    fun getImportedPlaylistDetails(playlistId: String): Flow<ImportedPlaylistDetailState> {
        return flow {
            val cachedPlaylist = _playlistDetailsCache.get(playlistId)
            if (cachedPlaylist != null) {
                emit(
                    ImportedPlaylistDetailState(
                        isLoading = false,
                        playlist = cachedPlaylist,
                        tracks = _playlistTracksCache.get(playlistId).orEmpty()
                    )
                )
                return@flow
            }

            val lightweightPlaylist = _importedPlaylists.value.find { it.id == playlistId }
            emit(ImportedPlaylistDetailState(isLoading = true, playlist = lightweightPlaylist))

            if (_offlineModeEnabled.value) {
                emit(
                    ImportedPlaylistDetailState(
                        isLoading = false,
                        playlist = lightweightPlaylist,
                        errorMessage = "Turn off Offline mode to refresh imported playlist songs."
                    )
                )
                return@flow
            }

            importRepository.getPlaylistDetails(playlistId)
                .onSuccess { playlist ->
                    Log.d("WavifyViewModel", "Loaded details for playlist id=$playlistId, track_ids.size=${playlist.track_ids.size}, total_tracks=${playlist.total_tracks}, tracks.size=${playlist.tracks.size}, details_fetched=true")
                    cacheImportedPlaylistDetails(playlist)
                    emit(
                        ImportedPlaylistDetailState(
                            isLoading = false,
                            playlist = playlist,
                            tracks = _playlistTracksCache.get(playlistId).orEmpty()
                        )
                    )
                }
                .onFailure { error ->
                    Log.e("WavifyViewModel", "Failed to fetch playlist details", error)
                    emit(
                        ImportedPlaylistDetailState(
                            isLoading = false,
                            playlist = lightweightPlaylist,
                            errorMessage = error.message ?: "Failed to load playlist songs"
                        )
                    )
                }
        }
    }

    fun getImportedPlaylistTracks(playlistId: String): Flow<List<Track>> {
        return flow {
            val cached = _playlistTracksCache.get(playlistId)
            if (cached != null) {
                emit(cached)
                return@flow
            }
            if (_offlineModeEnabled.value) {
                emit(emptyList())
                return@flow
            }
            importRepository.getPlaylistDetails(playlistId)
                .onSuccess { playlist ->
                    Log.d("WavifyViewModel", "Loaded details for playlist id=$playlistId, track_ids.size=${playlist.track_ids.size}, total_tracks=${playlist.total_tracks}, tracks.size=${playlist.tracks.size}, details_fetched=true")
                    cacheImportedPlaylistDetails(playlist)
                    emit(_playlistTracksCache.get(playlistId).orEmpty())
                }
                .onFailure { Log.e("WavifyViewModel", "Failed to fetch playlist tracks", it) }
        }
    }

    // Local Playlist actions
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val color = String.format("#%06X", (0xFFFFFF and (Math.random() * 0xFFFFFF).toInt()))
            playlistDao.createPlaylist(LocalPlaylist(name = name, coverColor = color))
        }
    }

    fun saveImportedPlaylistAsLocal(playlist: ImportedPlaylist, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                importRepository.getPlaylistDetails(playlist.id)
                    .onSuccess { fullPlaylist ->
                        val color = String.format("#%06X", (0xFFFFFF and (Math.random() * 0xFFFFFF).toInt()))
                        val playlistId = playlistDao.createPlaylist(
                            LocalPlaylist(
                                name = playlist.name,
                                coverColor = color
                            )
                        )
                        fullPlaylist.tracks.forEachIndexed { index, track ->
                            playlistDao.addTrackToPlaylist(
                                LocalPlaylistTrack(
                                    playlistId = playlistId,
                                    driveFileId = track.driveFileId ?: track.id.orEmpty(),
                                    position = index
                                )
                            )
                        }
                        withContext(Dispatchers.Main) { onResult(true) }
                    }
                    .onFailure {
                        Log.e("WavifyViewModel", "Failed to load tracks for import copy", it)
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
            } catch (e: Exception) {
                Log.e("WavifyViewModel", "Exception saving playlist", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val count = playlistDao.getTrackCount(playlistId)
            playlistDao.addTrackToPlaylist(LocalPlaylistTrack(playlistId, track.driveFileId, count))
        }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
        return combine(_tracks, playlistDao.getPlaylistTracks(playlistId), _offlineModeEnabled) { tracks, pTracks, offlineMode ->
            val trackByDriveFileId = tracks.associateBy { it.driveFileId }
            pTracks.mapNotNull { pt -> trackByDriveFileId[pt.driveFileId] }
                .visibleForOfflineMode(offlineMode)
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist)
        }
    }

    fun renamePlaylist(playlist: LocalPlaylist, newName: String) {
        viewModelScope.launch {
            playlistDao.updatePlaylist(playlist.copy(name = newName))
        }
    }
    
    fun removeTrackFromPlaylist(playlistId: Long, driveFileId: String) {
        viewModelScope.launch {
            playlistDao.removeTrackFromPlaylist(playlistId, driveFileId)
        }
    }

    fun setTrack(track: Track, playlist: List<Track> = _tracks.value) {
        hasUserInitiatedPlayback = true
        player?.let { p ->
            val defaultSource = _tracks.value.visibleForOfflineMode(_offlineModeEnabled.value)
            val requestedSource = playlist.ifEmpty { defaultSource }.ifEmpty { listOf(track) }
            val sourceForContext = if (requestedSource.any { it.id == track.id }) {
                requestedSource
            } else {
                listOf(track) + requestedSource
            }
            originalPlaybackSource = sourceForContext

            val playbackSource = if (_isShuffleEnabled.value) {
                listOf(track) + sourceForContext.filterNot { it.id == track.id }.shuffled()
            } else {
                sourceForContext
            }

            val sourceIndex = playbackSource.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            _manualQueue.value = emptyList()
            currentPlaybackSource = playbackSource
            currentPlaybackSourceIndex = sourceIndex
            isCurrentTrackFromManualQueue = false

            val playbackQueue = buildPlayerQueueFromCurrent(track)
            val newQueueIds = playbackQueue.map { it.id }

            if (newQueueIds != currentQueueIds) {
                currentQueueIds = newQueueIds
                loadedQueueEndExclusive = calculateLoadedNaturalEndExclusive()
                p.setMediaItems(playbackQueue.map { it.toMediaItem() }, 0, C.TIME_UNSET)
                p.prepare()
            } else {
                p.seekToDefaultPosition(0)
            }
            _currentTrack.value = playbackQueue.firstOrNull() ?: track
            syncPlaybackQueueState()
            p.play()
        }
    }

    fun startRadioFromTrack(track: Track? = _currentTrack.value) {
        val target = track ?: return
        val normalizedGenre = normalizeGenreName(target.genre)
        val availableTracks = _tracks.value.visibleForOfflineMode(_offlineModeEnabled.value)
        val radioSource = availableTracks
            .filter { candidate ->
                candidate.id == target.id ||
                        (normalizedGenre.isNotBlank() &&
                                normalizeGenreName(candidate.genre) == normalizedGenre)
            }
            .distinctBy { it.id }
            .let { genreTracks ->
                if (genreTracks.size > 1) {
                    listOf(target) + genreTracks.filterNot { it.id == target.id }.shuffled()
                } else {
                    listOf(target) + availableTracks.filterNot { it.id == target.id }.shuffled().take(INITIAL_UPCOMING_QUEUE_SIZE)
                }
            }

        setTrack(target, radioSource)
    }

    private fun Track.toMediaItem(): MediaItem {
        val streamUrl = "${BuildConfig.WAVIFY_PROXY_BASE_URL}/stream/$driveFileId"
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(albumArt))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildNaturalUpcoming(limit: Int = INITIAL_UPCOMING_QUEUE_SIZE): List<Track> {
        if (currentPlaybackSource.isEmpty() || currentPlaybackSourceIndex < 0) return emptyList()
        return currentPlaybackSource
            .drop(currentPlaybackSourceIndex + 1)
            .take(limit)
    }

    private fun buildPlayerQueueFromCurrent(current: Track): List<Track> {
        return listOf(current) + _manualQueue.value + buildNaturalUpcoming()
    }

    private fun calculateLoadedNaturalEndExclusive(): Int {
        if (currentPlaybackSource.isEmpty() || currentPlaybackSourceIndex < 0) return 0
        return (currentPlaybackSourceIndex + 1 + INITIAL_UPCOMING_QUEUE_SIZE).coerceAtMost(currentPlaybackSource.size)
    }

    private fun syncPlaybackQueueState() {
        val manualItems = _manualQueue.value.map { QueueItem(track = it, isManual = true) }
        val naturalItems = buildNaturalUpcoming().map { QueueItem(track = it, isManual = false) }
        _playbackQueue.value = manualItems + naturalItems
        prefetchNextQueuedTrack()
    }

    @OptIn(UnstableApi::class)
    private fun prefetchNextQueuedTrack() {
        val nextTrack = _playbackQueue.value.firstOrNull()?.track ?: return
        if (nextTrack.id == lastPrefetchedTrackId || nextTrack.driveFileId.isBlank()) return

        audioPrefetchJob?.cancel()
        lastPrefetchedTrackId = nextTrack.id
        audioPrefetchJob = viewModelScope.launch {
            runCatching { audioPrefetcher.prefetch(nextTrack) }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        Log.d("WavifyPrefetch", "Next-track prefetch skipped: ${error::class.java.simpleName}")
                    }
                }
        }
    }

    private fun rebuildCurrentPlayerQueue(startPositionMs: Long? = null, playWhenReady: Boolean = false) {
        val p = player ?: return
        val current = _currentTrack.value ?: return
        val wasPlaying = p.isPlaying
        val currentPosition = startPositionMs ?: p.currentPosition.coerceAtLeast(0L)
        val playbackQueue = buildPlayerQueueFromCurrent(current)
        if (playbackQueue.isEmpty()) return

        currentQueueIds = playbackQueue.map { it.id }
        loadedQueueEndExclusive = calculateLoadedNaturalEndExclusive()
        p.setMediaItems(playbackQueue.map { it.toMediaItem() }, 0, currentPosition)
        p.prepare()
        if (wasPlaying || playWhenReady) {
            p.play()
        }
        syncPlaybackQueueState()
    }

    private fun refreshCurrentQueueIdsFromPlayer(player: Player) {
        currentQueueIds = List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).mediaId
        }
    }

    private fun manualQueueInsertStartIndex(player: Player): Int? {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return null
        return currentIndex + 1
    }

    private fun insertManualMediaItem(track: Track, manualIndex: Int): Boolean {
        val p = player ?: return false
        val startIndex = manualQueueInsertStartIndex(p) ?: return false
        val insertIndex = (startIndex + manualIndex).coerceIn(0, p.mediaItemCount)
        p.addMediaItem(insertIndex, track.toMediaItem())
        refreshCurrentQueueIdsFromPlayer(p)
        syncPlaybackQueueState()
        return true
    }

    private fun removeManualMediaItem(manualIndex: Int): Boolean {
        val p = player ?: return false
        val startIndex = manualQueueInsertStartIndex(p) ?: return false
        val removeIndex = startIndex + manualIndex
        if (removeIndex !in 0 until p.mediaItemCount) return false
        p.removeMediaItem(removeIndex)
        refreshCurrentQueueIdsFromPlayer(p)
        syncPlaybackQueueState()
        return true
    }

    private fun moveManualMediaItem(fromIndex: Int, toIndex: Int): Boolean {
        val p = player ?: return false
        val startIndex = manualQueueInsertStartIndex(p) ?: return false
        val fromPlayerIndex = startIndex + fromIndex
        val toPlayerIndex = startIndex + toIndex
        if (fromPlayerIndex !in 0 until p.mediaItemCount || toPlayerIndex !in 0 until p.mediaItemCount) return false
        p.moveMediaItem(fromPlayerIndex, toPlayerIndex)
        refreshCurrentQueueIdsFromPlayer(p)
        syncPlaybackQueueState()
        return true
    }

    private fun clearManualMediaItems(count: Int): Boolean {
        val p = player ?: return false
        val startIndex = manualQueueInsertStartIndex(p) ?: return false
        val endExclusive = (startIndex + count).coerceAtMost(p.mediaItemCount)
        if (startIndex !in 0..p.mediaItemCount || startIndex >= endExclusive) return false
        for (index in endExclusive - 1 downTo startIndex) {
            p.removeMediaItem(index)
        }
        refreshCurrentQueueIdsFromPlayer(p)
        syncPlaybackQueueState()
        return true
    }

    private fun appendMoreQueueItemsIfNeeded(player: Player) {
        if (currentPlaybackSource.isEmpty()) return
        if (_manualQueue.value.isNotEmpty()) {
            syncPlaybackQueueState()
            return
        }
        if (loadedQueueEndExclusive >= currentPlaybackSource.size) return

        val currentMediaIndex = player.currentMediaItemIndex
        if (currentMediaIndex == C.INDEX_UNSET) return

        val remainingLoadedItems = player.mediaItemCount - currentMediaIndex - 1
        if (remainingLoadedItems > QUEUE_APPEND_THRESHOLD) return

        val nextEnd = (loadedQueueEndExclusive + QUEUE_APPEND_SIZE).coerceAtMost(currentPlaybackSource.size)
        val nextTracks = currentPlaybackSource.subList(loadedQueueEndExclusive, nextEnd)
        if (nextTracks.isEmpty()) return

        player.addMediaItems(nextTracks.map { it.toMediaItem() })
        currentQueueIds = currentQueueIds + nextTracks.map { it.id }
        loadedQueueEndExclusive = nextEnd
        syncPlaybackQueueState()
    }

    fun playNext(track: Track) {
        hasUserInitiatedPlayback = true
        if (_currentTrack.value == null) {
            setTrack(track)
            return
        }
        _manualQueue.value = listOf(track) + _manualQueue.value
        if (!insertManualMediaItem(track, manualIndex = 0)) {
            rebuildCurrentPlayerQueue()
        }
    }

    fun addToQueue(track: Track) {
        hasUserInitiatedPlayback = true
        if (_currentTrack.value == null) {
            setTrack(track)
            return
        }
        val insertIndex = _manualQueue.value.size
        _manualQueue.value = _manualQueue.value + track
        if (!insertManualMediaItem(track, manualIndex = insertIndex)) {
            rebuildCurrentPlayerQueue()
        }
    }

    fun removeManualQueueItem(index: Int) {
        val queue = _manualQueue.value.toMutableList()
        if (index !in queue.indices) return
        queue.removeAt(index)
        _manualQueue.value = queue
        if (!removeManualMediaItem(index)) {
            rebuildCurrentPlayerQueue()
        }
    }

    fun moveManualQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _manualQueue.value.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val moved = queue.removeAt(fromIndex)
        queue.add(toIndex, moved)
        _manualQueue.value = queue
        if (!moveManualMediaItem(fromIndex, toIndex)) {
            rebuildCurrentPlayerQueue()
        }
    }

    fun clearManualQueue() {
        val count = _manualQueue.value.size
        if (count == 0) return
        _manualQueue.value = emptyList()
        if (!clearManualMediaItems(count)) {
            rebuildCurrentPlayerQueue()
        }
    }

    fun playQueueItem(queueIndex: Int) {
        val queueSnapshot = _playbackQueue.value
        val selectedItem = queueSnapshot.getOrNull(queueIndex) ?: return
        hasUserInitiatedPlayback = true

        if (selectedItem.isManual) {
            val manualIndex = queueSnapshot.take(queueIndex).count { it.isManual }
            val manualTracks = _manualQueue.value.toMutableList()
            if (manualIndex in manualTracks.indices) {
                manualTracks.removeAt(manualIndex)
                _manualQueue.value = manualTracks.drop(manualIndex)
            }
        } else {
            _manualQueue.value = emptyList()
            val sourceIndex = currentPlaybackSource.indexOfFirst { it.id == selectedItem.track.id }
            if (sourceIndex >= 0) {
                currentPlaybackSourceIndex = sourceIndex
            }
        }

        isCurrentTrackFromManualQueue = selectedItem.isManual
        _currentTrack.value = selectedItem.track
        rebuildCurrentPlayerQueue(startPositionMs = 0L, playWhenReady = true)
    }

    fun play() {
        hasUserInitiatedPlayback = true
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        hasUserInitiatedPlayback = true
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceIn(0L, _playbackDuration.value)
        _playbackPosition.value = safePosition
        player?.seekTo(safePosition)
    }

    fun playNextTrack() {
        hasUserInitiatedPlayback = true
        player?.seekToNextMediaItem()
    }

    fun playPreviousTrack() {
        hasUserInitiatedPlayback = true
        player?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        player?.shuffleModeEnabled = false

        val current = _currentTrack.value ?: return
        if (originalPlaybackSource.isEmpty()) return

        if (isCurrentTrackFromManualQueue) {
            val anchorIndex = currentPlaybackSourceIndex.coerceIn(-1, originalPlaybackSource.lastIndex)
            currentPlaybackSource = if (_isShuffleEnabled.value) {
                originalPlaybackSource.take(anchorIndex + 1) + originalPlaybackSource.drop(anchorIndex + 1).shuffled()
            } else {
                originalPlaybackSource
            }
            currentPlaybackSourceIndex = anchorIndex
        } else if (originalPlaybackSource.any { it.id == current.id }) {
            currentPlaybackSource = if (_isShuffleEnabled.value) {
                listOf(current) + originalPlaybackSource.filterNot { it.id == current.id }.shuffled()
            } else {
                originalPlaybackSource
            }
            currentPlaybackSourceIndex = currentPlaybackSource.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
        } else {
            val anchorIndex = currentPlaybackSourceIndex.coerceIn(-1, originalPlaybackSource.lastIndex)
            currentPlaybackSource = if (_isShuffleEnabled.value) {
                originalPlaybackSource.take(anchorIndex + 1) + originalPlaybackSource.drop(anchorIndex + 1).shuffled()
            } else {
                originalPlaybackSource
            }
            currentPlaybackSourceIndex = anchorIndex
        }
        rebuildCurrentPlayerQueue()
    }

    fun toggleRepeat() {
        _isRepeatEnabled.value = !_isRepeatEnabled.value
        player?.repeatMode = if (_isRepeatEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun startSleepTimer(minutes: Int) {
        val totalMillis = minutes.coerceAtLeast(1) * 60_000L
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMillis.value = totalMillis
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMillis
            while (remaining > 0L && isActive) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _sleepTimerRemainingMillis.value = remaining
            }
            if (isActive) {
                player?.pause()
                _isPlaying.value = false
                _sleepTimerRemainingMillis.value = 0L
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMillis.value = 0L
    }

    fun toggleFavorite(trackId: String) {
        val current = _favoriteTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _favoriteTrackIds.value = current
    }

    private fun observeDownloadWork() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagLiveData(TrackDownloadWorker.TAG).asFlow().collectLatest { workInfos ->
                val activeDownloads = mutableMapOf<String, Float>()
                workInfos.forEach { workInfo ->
                    val trackId = TrackDownloadWorker.trackIdFromTags(workInfo.tags) ?: return@forEach
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.BLOCKED -> {
                            val progressPercent = workInfo.progress.getInt(TrackDownloadWorker.KEY_PROGRESS, 0)
                            activeDownloads[trackId] = (progressPercent / 100f).coerceIn(0f, 1f)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            if (handledDownloadWorkIds.add(workInfo.id)) {
                                markTrackDownloaded(
                                    trackId = workInfo.outputData.getString(TrackDownloadWorker.KEY_TRACK_ID) ?: trackId,
                                    driveFileId = workInfo.outputData.getString(TrackDownloadWorker.KEY_DRIVE_FILE_ID),
                                    localFilePath = workInfo.outputData.getString(TrackDownloadWorker.KEY_LOCAL_FILE_PATH)
                                )
                                workManager.pruneWork()
                            }
                        }
                        WorkInfo.State.FAILED,
                        WorkInfo.State.CANCELLED -> {
                            handledDownloadWorkIds.add(workInfo.id)
                        }
                    }
                }
                _downloadProgress.value = activeDownloads
            }
        }
    }

    private fun markTrackDownloaded(
        trackId: String,
        driveFileId: String?,
        localFilePath: String?
    ) {
        val resolvedDriveFileId = driveFileId?.takeIf { it.isNotBlank() } ?: return
        val resolvedPath = localFilePath?.takeIf { it.isNotBlank() }
            ?: downloadManager.downloadedFile(resolvedDriveFileId).absolutePath
        _tracks.value = _tracks.value.map { track ->
            if (track.id == trackId || track.driveFileId == resolvedDriveFileId) {
                track.copy(isDownloaded = true, localFilePath = resolvedPath)
            } else {
                track
            }
        }
    }

    fun downloadTrack(trackId: String) {
        if (_downloadProgress.value.containsKey(trackId)) return
        val currentTrackList = _tracks.value
        val target = currentTrackList.find { it.id == trackId } ?: return
        if (target.isDownloaded) return
        val driveFileId = target.driveFileId.takeIf { it.isNotBlank() } ?: return

        _downloadProgress.value = _downloadProgress.value + (trackId to 0f)
        workManager.enqueueUniqueWork(
            TrackDownloadWorker.uniqueWorkName(driveFileId),
            ExistingWorkPolicy.KEEP,
            TrackDownloadWorker.buildRequest(trackId, driveFileId)
        )
    }

    fun deleteDownloadedTrack(trackId: String) {
        viewModelScope.launch {
            val target = _tracks.value.find { it.id == trackId }
            target?.driveFileId?.let { driveFileId ->
                workManager.cancelUniqueWork(TrackDownloadWorker.uniqueWorkName(driveFileId))
                _downloadProgress.value = _downloadProgress.value - trackId
                downloadManager.deleteTrack(driveFileId)
                withContext(Dispatchers.IO) {
                    downloadedTrackDao.delete(driveFileId)
                }
            }
            
            repository.deleteDownload(trackId)
            _tracks.value = _tracks.value.map {
                if (it.id == trackId || it.driveFileId == target?.driveFileId) {
                    it.copy(isDownloaded = false, localFilePath = null)
                } else {
                    it
                }
            }
        }
    }

    fun refreshTracks() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (!_offlineModeEnabled.value) {
                loadTracks()
                loadImportedPlaylists()
                loadTopArtists()
            }
            delay(500)
            _isRefreshing.value = false
        }
    }

    @OptIn(UnstableApi::class)
    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            _isPlaying.collectLatest { isPlaying ->
                if (isPlaying) {
                    while (true) {
                        updatePlaybackState()
                        delay(250L)
                    }
                } else {
                    updatePlaybackState()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updatePlaybackState() {
        player?.let { p ->
            // Always update position and duration even when paused for UI sync
            _playbackPosition.value = p.currentPosition
            val duration = p.duration
            if (duration != C.TIME_UNSET && duration > 0) {
                _playbackDuration.value = duration
            }

            if (p.isPlaying) {
                // Heuristic for next song preloading indicator
                val position = p.currentPosition
                val totalBuffered = p.totalBufferedDuration
                
                if (duration != C.TIME_UNSET && duration > 0) {
                    val remainingCurrent = duration - position
                    val nextIndex = p.nextMediaItemIndex
                    
                    if (nextIndex != C.INDEX_UNSET && remainingCurrent < 20000) {
                        _isNextTrackLoading.value = totalBuffered < (remainingCurrent + 5000)
                    } else {
                        _isNextTrackLoading.value = false
                    }
                } else {
                    _isNextTrackLoading.value = false
                }
            } else {
                _isNextTrackLoading.value = false
            }
        }
    }

    override fun onCleared() {
        playbackTickerJob?.cancel()
        sleepTimerJob?.cancel()
        audioPrefetchJob?.cancel()
        browserFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        super.onCleared()
    }
}
