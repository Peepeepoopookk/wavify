package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<CachedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<CachedTrackEntity>)

    @Query("DELETE FROM cached_tracks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tracks: List<CachedTrackEntity>) {
        clear()
        insertAll(tracks)
    }
}
