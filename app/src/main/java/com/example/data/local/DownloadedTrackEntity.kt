package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloaded_tracks",
    indices = [Index(value = ["downloadedAt"])]
)
data class DownloadedTrackEntity(
    @PrimaryKey val driveFileId: String,
    val localFilePath: String,
    val downloadedAt: Long,
    val fileSizeBytes: Long
)
