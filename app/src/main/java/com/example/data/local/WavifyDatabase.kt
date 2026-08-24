package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalPlaylist::class,
        LocalPlaylistTrack::class,
        RecentlyPlayedTrack::class,
        DownloadedTrackEntity::class,
        CachedTrackEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class WavifyDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun cachedTrackDao(): CachedTrackDao

    companion object {
        @Volatile
        private var INSTANCE: WavifyDatabase? = null

        fun getDatabase(context: Context): WavifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `downloaded_tracks` (" +
                            "`driveFileId` TEXT NOT NULL, " +
                            "`localFilePath` TEXT NOT NULL, " +
                            "`downloadedAt` INTEGER NOT NULL, " +
                            "`fileSizeBytes` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`driveFileId`))"
                        )
                    }
                }

                val MIGRATION_2_3 = object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_playlists_createdAt` ON `local_playlists` (`createdAt`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_playlist_tracks_playlistId_position` ON `local_playlist_tracks` (`playlistId`, `position`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_playlist_tracks_driveFileId` ON `local_playlist_tracks` (`driveFileId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recently_played_playedAt` ON `recently_played` (`playedAt`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_downloadedAt` ON `downloaded_tracks` (`downloadedAt`)")
                    }
                }

                val MIGRATION_3_4 = object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cached_tracks` (" +
                                "`id` TEXT NOT NULL, " +
                                "`driveFileId` TEXT NOT NULL, " +
                                "`title` TEXT NOT NULL, " +
                                "`artist` TEXT NOT NULL, " +
                                "`album` TEXT NOT NULL, " +
                                "`genre` TEXT NOT NULL, " +
                                "`duration` TEXT NOT NULL, " +
                                "`durationSeconds` INTEGER NOT NULL, " +
                                "`spotifyId` TEXT, " +
                                "`albumArt` TEXT NOT NULL, " +
                                "`streamUrl` TEXT NOT NULL, " +
                                "`language` TEXT NOT NULL, " +
                                "`source` TEXT NOT NULL, " +
                                "`requestedBy` TEXT, " +
                                "`lyrics` TEXT, " +
                                "`syncedLyrics` TEXT, " +
                                "`lyricsStatus` TEXT NOT NULL, " +
                                "`timestamp` TEXT, " +
                                "`addedAt` TEXT, " +
                                "`updatedAt` TEXT, " +
                                "`cachedAt` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`id`))"
                        )
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_tracks_driveFileId` ON `cached_tracks` (`driveFileId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_genre` ON `cached_tracks` (`genre`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_language` ON `cached_tracks` (`language`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_updatedAt` ON `cached_tracks` (`updatedAt`)")
                    }
                }

                val MIGRATION_4_5 = object : Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `downloaded_tracks` ADD COLUMN `albumArtLocalPath` TEXT")
                    }
                }
            
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WavifyDatabase::class.java,
                    "wavify_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
