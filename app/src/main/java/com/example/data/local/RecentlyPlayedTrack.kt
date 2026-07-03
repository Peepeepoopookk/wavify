package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recently_played",
    indices = [Index(value = ["playedAt"])]
)
data class RecentlyPlayedTrack(
    @PrimaryKey val driveFileId: String,
    val playedAt: Long = System.currentTimeMillis()
)
