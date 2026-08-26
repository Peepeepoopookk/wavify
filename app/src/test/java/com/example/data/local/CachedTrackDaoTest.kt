package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CachedTrackDaoTest {

    private lateinit var database: WavifyDatabase
    private lateinit var dao: CachedTrackDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WavifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cachedTrackDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTrack(id: String, title: String): CachedTrackEntity {
        return CachedTrackEntity(
            id = id,
            driveFileId = "drive_$id",
            title = title,
            artist = "artist",
            album = "album",
            genre = "genre",
            duration = "0:00",
            durationSeconds = 0,
            spotifyId = null,
            albumArt = "art",
            streamUrl = "url",
            language = "lang",
            source = "src",
            requestedBy = null,
            lyrics = null,
            syncedLyrics = null,
            lyricsStatus = "none",
            timestamp = null,
            addedAt = null,
            updatedAt = null,
            cachedAt = 0L
        )
    }

    @Test
    fun testReplaceAll_upsertsNewAndDeletesMissing() = runBlocking {
        // Arrange
        val initialTracks = listOf(
            createTrack("1", "title1"),
            createTrack("2", "title2")
        )
        dao.replaceAll(initialTracks)
        
        var dbTracks = dao.getAllTracks().first()
        assertEquals(2, dbTracks.size)

        // Act - Update track 1, remove track 2, add track 3
        val newTracks = listOf(
            createTrack("1", "title1_updated"),
            createTrack("3", "title3")
        )
        dao.replaceAll(newTracks)

        // Assert
        dbTracks = dao.getAllTracks().first()
        assertEquals(2, dbTracks.size)
        
        val track1 = dbTracks.find { it.id == "1" }
        assertEquals("title1_updated", track1?.title) // Updated
        
        val track2 = dbTracks.find { it.id == "2" }
        assertEquals(null, track2) // Deleted
        
        val track3 = dbTracks.find { it.id == "3" }
        assertEquals("title3", track3?.title) // Inserted
    }

    @Test
    fun testReplaceAll_emptyListClearsTable() = runBlocking {
        val initialTracks = listOf(
            createTrack("1", "title1")
        )
        dao.replaceAll(initialTracks)
        assertEquals(1, dao.getAllTracks().first().size)

        dao.replaceAll(emptyList())
        assertEquals(0, dao.getAllTracks().first().size)
    }
}
