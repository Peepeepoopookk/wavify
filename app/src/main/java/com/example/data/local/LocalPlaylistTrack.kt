package com.example.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_playlist_tracks",
    primaryKeys = ["playlistId", "driveFileId"],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["driveFileId"])
    ]
)
data class LocalPlaylistTrack(
    val playlistId: Long,
    val driveFileId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
