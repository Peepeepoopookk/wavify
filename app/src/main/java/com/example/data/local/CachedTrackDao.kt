package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileId(driveFileId: String): CachedTrackEntity?

    @Upsert
    suspend fun upsertAll(tracks: List<CachedTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<CachedTrackEntity>)

    @Query("DELETE FROM cached_tracks WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM cached_tracks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tracks: List<CachedTrackEntity>) {
        if (tracks.isEmpty()) {
            clear()
        } else {
            upsertAll(tracks)
            deleteNotIn(tracks.map { it.id })
        }
    }
}
