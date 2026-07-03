package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: LocalPlaylist): Long

    @Delete
    suspend fun deletePlaylist(playlist: LocalPlaylist)

    @Update
    suspend fun updatePlaylist(playlist: LocalPlaylist)

    @Query("SELECT * FROM local_playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<LocalPlaylist>>

    @Query("SELECT * FROM local_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): LocalPlaylist?

    // Playlist Tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(track: LocalPlaylistTrack)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId AND driveFileId = :driveFileId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, driveFileId: String)

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistTracks(playlistId: Long): Flow<List<LocalPlaylistTrack>>

    @Query("SELECT COUNT(*) FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: Long): Int

    @Transaction
    suspend fun reorderTracks(tracks: List<LocalPlaylistTrack>) {
        tracks.forEach { updateTrackPosition(it.playlistId, it.driveFileId, it.position) }
    }

    @Query("UPDATE local_playlist_tracks SET position = :position WHERE playlistId = :playlistId AND driveFileId = :driveFileId")
    suspend fun updateTrackPosition(playlistId: Long, driveFileId: String, position: Int)

    // History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(track: RecentlyPlayedTrack)

    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 10")
    fun getRecentlyPlayed(): Flow<List<RecentlyPlayedTrack>>
}
