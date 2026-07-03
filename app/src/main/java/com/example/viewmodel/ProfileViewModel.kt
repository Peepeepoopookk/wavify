package com.example.viewmodel

import android.app.Application
import coil.imageLoader
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.UserPreferences
import com.example.repository.UserPreferencesRepository
import com.example.repository.clearApiCache
import com.example.service.MusicCache
import com.example.data.local.WavifyDatabase
import com.example.download.DownloadManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserPreferencesRepository(application)

    val userPreferences: StateFlow<UserPreferences> = repository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences(
                displayName = "User",
                avatarColor = 0xFF6200EE.toInt(),
                theme = "Dark",
                languageFilter = "All",
                audioQuality = "Normal",
                profilePicturePath = "",
                deviceId = "",
                musicCacheLimitMb = 500,
                prefetchOnCellular = false,
                offlineMode = false
            )
        )

    init {
        viewModelScope.launch {
            repository.ensureDeviceId()
        }
    }

    fun updateProfilePicturePath(path: String) {
        viewModelScope.launch {
            repository.updateProfilePicturePath(path)
        }
    }

    fun removeProfilePicture() {
        viewModelScope.launch {
            repository.updateProfilePicturePath("")
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            repository.updateDisplayName(name)
        }
    }

    fun updateAvatarColor(color: Int) {
        viewModelScope.launch {
            repository.updateAvatarColor(color)
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            repository.updateTheme(theme)
        }
    }

    fun updateLanguageFilter(filter: String) {
        viewModelScope.launch {
            repository.updateLanguageFilter(filter)
        }
    }

    fun updateAudioQuality(quality: String) {
        viewModelScope.launch {
            repository.updateAudioQuality(quality)
        }
    }

    fun updateMusicCacheLimit(limitLabel: String) {
        val limitMb = when (limitLabel) {
            "250MB" -> 250
            "500MB" -> 500
            "1GB" -> 1024
            else -> 500
        }
        viewModelScope.launch {
            repository.updateMusicCacheLimitMb(limitMb)
            MusicCache.setMaxCacheSizeBytes(limitMb * 1024L * 1024L)
        }
    }

    fun updatePrefetchOnCellular(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePrefetchOnCellular(enabled)
        }
    }

    fun updateOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateOfflineMode(enabled)
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearAppCaches() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            MusicCache.clear(app)
            clearApiCache(app)
            app.imageLoader.memoryCache?.clear()
            app.imageLoader.diskCache?.clear()
        }
    }
    
    fun clearDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            val downloadManager = DownloadManager(app)
            val downloadedTrackDao = WavifyDatabase.getDatabase(app).downloadedTrackDao()
            downloadedTrackDao.getAllSync().forEach { downloadedTrack ->
                downloadManager.deleteTrack(downloadedTrack.driveFileId)
            }
            downloadedTrackDao.clearAll()
        }
    }
}
