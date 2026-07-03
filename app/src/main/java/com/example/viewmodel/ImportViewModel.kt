package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Track
import com.example.model.resolveAlbumArt
import com.example.repository.ImportRepository
import com.example.repository.UserPreferencesRepository
import com.example.service.ImportHistoryItem
import com.example.service.ImportStatsResponse
import com.example.service.PlaylistPreviewResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val importRepository = ImportRepository(application)
    private val userPrefsRepository = UserPreferencesRepository(application)

    // Device ID state
    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // Add Song state
    private val _songUrl = MutableStateFlow("")
    val songUrl: StateFlow<String> = _songUrl.asStateFlow()

    private val _songPreview = MutableStateFlow<Track?>(null)
    val songPreview: StateFlow<Track?> = _songPreview.asStateFlow()

    private val _songStatus = MutableStateFlow<String>("")
    val songStatus: StateFlow<String> = _songStatus.asStateFlow()

    private val _isSongLoading = MutableStateFlow(false)
    val isSongLoading: StateFlow<Boolean> = _isSongLoading.asStateFlow()

    // Playlist Import state
    private val _playlistUrl = MutableStateFlow("")
    val playlistUrl: StateFlow<String> = _playlistUrl.asStateFlow()

    private val _playlistPreview = MutableStateFlow<PlaylistPreviewResponse?>(null)
    val playlistPreview: StateFlow<PlaylistPreviewResponse?> = _playlistPreview.asStateFlow()

    private val _playlistOverallStatus = MutableStateFlow("") // "Running", "Completed", "Error"
    val playlistOverallStatus: StateFlow<String> = _playlistOverallStatus.asStateFlow()

    private val _isPlaylistLoading = MutableStateFlow(false)
    val isPlaylistLoading: StateFlow<Boolean> = _isPlaylistLoading.asStateFlow()

    // My Imports state
    private val _importHistory = MutableStateFlow<List<ImportHistoryItem>>(emptyList())
    val importHistory: StateFlow<List<ImportHistoryItem>> = _importHistory.asStateFlow()

    private val _importStats = MutableStateFlow<ImportStatsResponse?>(null)
    val importStats: StateFlow<ImportStatsResponse?> = _importStats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            // ensure device ID is generated if not exists
            val id = userPrefsRepository.ensureDeviceId()
            _deviceId.value = id
            loadImportHistoryAndStats()
        }
    }

    fun setSongUrl(url: String) {
        _songUrl.value = url
    }

    fun setPlaylistUrl(url: String) {
        _playlistUrl.value = url
    }

    fun addSong() {
        val url = _songUrl.value
        val id = _deviceId.value
        if (url.isEmpty() || id.isEmpty()) return

        _isSongLoading.value = true
        _songStatus.value = "Importing..."
        _songPreview.value = null

        viewModelScope.launch {
            importRepository.addSong(url, id).onSuccess { response ->
                _isSongLoading.value = false
                _songStatus.value = "Added to your library!"
                // create dummy Track for preview since we only get ImportedTrackResponse
                _songPreview.value = Track(
                    id = response.driveFileId ?: "",
                    title = response.title,
                    artist = response.artist,
                    album = "",
                    duration = response.duration ?: "0:00",
                    durationSeconds = 0,
                    albumArt = resolveAlbumArt(
                        primary = response.albumArt,
                        secondary = null,
                        seed = "${response.driveFileId.orEmpty()}-${response.title}-${response.artist}"
                    ),
                    streamUrl = "",
                    driveFileId = response.driveFileId ?: "",
                    genre = "",
                    language = ""
                )
                loadImportHistoryAndStats()
            }.onFailure { e ->
                _isSongLoading.value = false
                val errorMsg = e.message ?: "Unknown error"
                if (errorMsg.contains("duplicate", ignoreCase = true) || 
                    (e is HttpException && e.response()?.errorBody()?.string()?.contains("duplicate", ignoreCase = true) == true)) {
                    _songStatus.value = "Error: This song is already in the library"
                } else {
                    _songStatus.value = "Error: $errorMsg"
                }
            }
        }
    }

    fun previewPlaylist() {
        val url = _playlistUrl.value
        if (url.isEmpty()) return

        _isPlaylistLoading.value = true
        _playlistPreview.value = null

        viewModelScope.launch {
            importRepository.previewPlaylist(url).onSuccess { response ->
                _playlistPreview.value = response
                _isPlaylistLoading.value = false
            }.onFailure {
                _isPlaylistLoading.value = false
            }
        }
    }

    fun startPlaylistImport(onSuccess: () -> Unit = {}) {
        val url = _playlistUrl.value
        val id = _deviceId.value
        if (url.isEmpty() || id.isEmpty()) return

        _isPlaylistLoading.value = true
        _playlistOverallStatus.value = "Starting import..."

        viewModelScope.launch {
            importRepository.startPlaylistImport(url, id).onSuccess { response ->
                _playlistOverallStatus.value = "Import started, check back soon"
                _isPlaylistLoading.value = false
                onSuccess()
            }.onFailure { e ->
                _isPlaylistLoading.value = false
                val errorMsg = e.message ?: "Unknown error"
                _playlistOverallStatus.value = "Error: $errorMsg"
            }
        }
    }

    fun loadImportHistoryAndStats() {
        val id = _deviceId.value
        if (id.isEmpty()) return

        viewModelScope.launch {
            _isRefreshing.value = true
            
            // fetch both in parallel or sequentially
            importRepository.getMyImports(id).onSuccess { items ->
                _importHistory.value = items
            }
            importRepository.getImportStats(id).onSuccess { stats ->
                _importStats.value = stats
            }

            _isRefreshing.value = false
        }
    }
}
