package com.example.repository

import com.example.data.MockData
import com.example.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

interface MusicRepository {
    fun getMockTracks(): Flow<List<Track>>
    suspend fun toggleDownload(trackId: String): Boolean
    suspend fun deleteDownload(trackId: String): Boolean
}

class MusicRepositoryImpl : MusicRepository {
    private val tracksFlow = MutableStateFlow(MockData.tracks)

    override fun getMockTracks(): Flow<List<Track>> = tracksFlow

    override suspend fun toggleDownload(trackId: String): Boolean {
        var newStatus = false
        tracksFlow.value = tracksFlow.value.map { track ->
            if (track.id == trackId) {
                newStatus = !track.isDownloaded
                track.copy(isDownloaded = newStatus)
            } else {
                track
            }
        }
        return newStatus
    }

    override suspend fun deleteDownload(trackId: String): Boolean {
        tracksFlow.value = tracksFlow.value.map { track ->
            if (track.id == trackId) {
                track.copy(isDownloaded = false)
            } else {
                track
            }
        }
        return true
    }
}
