package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val displayName: String,
    val avatarColor: Int,
    val theme: String,
    val languageFilter: String,
    val audioQuality: String,
    val profilePicturePath: String,
    val deviceId: String,
    val musicCacheLimitMb: Int,
    val prefetchOnCellular: Boolean,
    val offlineMode: Boolean
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_COLOR = intPreferencesKey("avatar_color")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE_FILTER = stringPreferencesKey("language_filter")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val PROFILE_PICTURE_PATH = stringPreferencesKey("profile_picture_path")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val MUSIC_CACHE_LIMIT_MB = intPreferencesKey("music_cache_limit_mb")
        val PREFETCH_ON_CELLULAR = booleanPreferencesKey("prefetch_on_cellular")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                displayName = preferences[PreferencesKeys.DISPLAY_NAME] ?: "User",
                avatarColor = preferences[PreferencesKeys.AVATAR_COLOR] ?: 0xFF6200EE.toInt(),
                theme = preferences[PreferencesKeys.THEME] ?: "Dark",
                languageFilter = preferences[PreferencesKeys.LANGUAGE_FILTER] ?: "All",
                audioQuality = preferences[PreferencesKeys.AUDIO_QUALITY] ?: "Normal",
                profilePicturePath = preferences[PreferencesKeys.PROFILE_PICTURE_PATH] ?: "",
                deviceId = preferences[PreferencesKeys.DEVICE_ID] ?: "",
                musicCacheLimitMb = preferences[PreferencesKeys.MUSIC_CACHE_LIMIT_MB] ?: 500,
                prefetchOnCellular = preferences[PreferencesKeys.PREFETCH_ON_CELLULAR] ?: false,
                offlineMode = preferences[PreferencesKeys.OFFLINE_MODE] ?: false
            )
        }

    suspend fun ensureDeviceId(): String {
        var currentId = ""
        context.dataStore.edit { preferences ->
            currentId = preferences[PreferencesKeys.DEVICE_ID] ?: ""
            if (currentId.isEmpty()) {
                currentId = java.util.UUID.randomUUID().toString()
                preferences[PreferencesKeys.DEVICE_ID] = currentId
            }
        }
        return currentId
    }

    suspend fun updateProfilePicturePath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_PICTURE_PATH] = path
        }
    }

    suspend fun updateDisplayName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_NAME] = name
        }
    }

    suspend fun updateAvatarColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AVATAR_COLOR] = color
        }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme
        }
    }

    suspend fun updateLanguageFilter(filter: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE_FILTER] = filter
        }
    }

    suspend fun updateAudioQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_QUALITY] = quality
        }
    }

    suspend fun updateMusicCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MUSIC_CACHE_LIMIT_MB] = limitMb
        }
    }

    suspend fun updatePrefetchOnCellular(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFETCH_ON_CELLULAR] = enabled
        }
    }

    suspend fun updateOfflineMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OFFLINE_MODE] = enabled
        }
    }
}
