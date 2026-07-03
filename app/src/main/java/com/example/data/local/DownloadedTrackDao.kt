package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE driveFileId = :driveFileId")
    suspend fun delete(driveFileId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearAll()

    @Query("SELECT * FROM downloaded_tracks")
    fun getAll(): Flow<List<DownloadedTrackEntity>>
    
    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getAllSync(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE driveFileId = :driveFileId")
    suspend fun getByDriveFileId(driveFileId: String): DownloadedTrackEntity?
}
