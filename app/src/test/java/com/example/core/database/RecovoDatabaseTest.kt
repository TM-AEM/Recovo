package com.example.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.dao.BookmarkDao
import com.example.core.database.dao.FolderDao
import com.example.core.database.dao.RecordingDao
import com.example.core.database.dao.TagDao
import com.example.core.database.model.BookmarkEntity
import com.example.core.database.model.FolderEntity
import com.example.core.database.model.RecordingEntity
import com.example.core.database.model.RecordingTagCrossRef
import com.example.core.database.model.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RecovoDatabaseTest {

    private lateinit var database: RecovoDatabase
    private lateinit var recordingDao: RecordingDao
    private lateinit var folderDao: FolderDao
    private lateinit var tagDao: TagDao
    private lateinit var bookmarkDao: BookmarkDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecovoDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        recordingDao = database.recordingDao()
        folderDao = database.folderDao()
        tagDao = database.tagDao()
        bookmarkDao = database.bookmarkDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetRecordingById() = runTest {
        val recording = RecordingEntity(
            fileName = "REC_001.m4a",
            displayName = "Meeting Notes",
            filePath = "/data/user/0/com.example/files/recordings/REC_001.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 60000L,
            fileSizeBytes = 1024000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 2,
            createdAt = 1000L
        )

        val id = recordingDao.insert(recording)
        assertTrue(id > 0)

        val retrieved = recordingDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Meeting Notes", retrieved?.displayName)
        assertEquals("M4A", retrieved?.format)
        assertEquals(60000L, retrieved?.durationMs)
    }

    @Test
    fun updateRecording() = runTest {
        val recording = RecordingEntity(
            fileName = "REC_002.m4a",
            displayName = "Initial Name",
            filePath = "/path/REC_002.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 50000L,
            sampleRate = 48000,
            bitRate = 192000,
            channelCount = 1
        )
        val id = recordingDao.insert(recording)
        val saved = recordingDao.getById(id)!!

        val updated = saved.copy(displayName = "Updated Name", durationMs = 15000L)
        recordingDao.update(updated)

        val reloaded = recordingDao.getById(id)
        assertEquals("Updated Name", reloaded?.displayName)
        assertEquals(15000L, reloaded?.durationMs)
    }

    @Test
    fun deleteRecording() = runTest {
        val recording = RecordingEntity(
            fileName = "REC_003.m4a",
            displayName = "To Delete",
            filePath = "/path/REC_003.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 2000L,
            fileSizeBytes = 20000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val id = recordingDao.insert(recording)
        assertNotNull(recordingDao.getById(id))

        recordingDao.deleteById(id)
        assertNull(recordingDao.getById(id))
    }

    @Test
    fun searchRecordings() = runTest {
        recordingDao.insert(
            RecordingEntity(
                fileName = "REC_A.m4a",
                displayName = "Project Brainstorming",
                filePath = "/path/A.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 1000L,
                fileSizeBytes = 1000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1
            )
        )
        recordingDao.insert(
            RecordingEntity(
                fileName = "REC_B.m4a",
                displayName = "Shopping List",
                filePath = "/path/B.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 1000L,
                fileSizeBytes = 1000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1
            )
        )

        val results = recordingDao.searchRecordings("brain").first()
        assertEquals(1, results.size)
        assertEquals("Project Brainstorming", results[0].displayName)
    }

    @Test
    fun sortingRecordings() = runTest {
        recordingDao.insert(
            RecordingEntity(
                fileName = "REC_1.m4a",
                displayName = "Alpha",
                filePath = "/path/1.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 10000L,
                fileSizeBytes = 500L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                createdAt = 1000L
            )
        )
        recordingDao.insert(
            RecordingEntity(
                fileName = "REC_2.m4a",
                displayName = "Beta",
                filePath = "/path/2.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 20000L,
                fileSizeBytes = 1500L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                createdAt = 2000L
            )
        )

        val newest = recordingDao.observeAllNewest().first()
        assertEquals("Beta", newest[0].displayName)

        val byDuration = recordingDao.observeAllByDuration().first()
        assertEquals("Beta", byDuration[0].displayName)

        val byName = recordingDao.observeAllByName().first()
        assertEquals("Alpha", byName[0].displayName)
    }

    @Test
    fun bookmarkCascadeOnRecordingDelete() = runTest {
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "REC_BM.m4a",
                displayName = "With Bookmarks",
                filePath = "/path/BM.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 30000L,
                fileSizeBytes = 30000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1
            )
        )

        bookmarkDao.insert(
            BookmarkEntity(
                recordingId = recId,
                positionMs = 5000L,
                label = "Important Highlight"
            )
        )

        val bookmarks = bookmarkDao.getBookmarksForRecording(recId)
        assertEquals(1, bookmarks.size)
        assertEquals("Important Highlight", bookmarks[0].label)

        // Deleting the recording must cascade and delete bookmarks
        recordingDao.deleteById(recId)

        val bookmarksAfterDelete = bookmarkDao.getBookmarksForRecording(recId)
        assertTrue(bookmarksAfterDelete.isEmpty())
    }

    @Test
    fun folderAndTagsRelationship() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "Interviews"))
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "REC_INT.m4a",
                displayName = "Interview with Founder",
                filePath = "/path/INT.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 120000L,
                fileSizeBytes = 500000L,
                sampleRate = 48000,
                bitRate = 192000,
                channelCount = 2,
                folderId = folderId
            )
        )

        val recordingsInFolder = recordingDao.observeByFolder(folderId).first()
        assertEquals(1, recordingsInFolder.size)
        assertEquals("Interview with Founder", recordingsInFolder[0].displayName)

        val tagId = tagDao.insert(TagEntity(name = "Urgent"))
        tagDao.insertCrossRef(RecordingTagCrossRef(recordingId = recId, tagId = tagId))

        val tagsForRec = tagDao.observeTagsForRecording(recId).first()
        assertEquals(1, tagsForRec.size)
        assertEquals("Urgent", tagsForRec[0].name)
    }

    @Test
    fun favoriteOperations() = runTest {
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "REC_FAV.m4a",
                displayName = "Important Speech",
                filePath = "/path/FAV.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 5000L,
                fileSizeBytes = 5000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                isFavorite = false
            )
        )

        var loaded = recordingDao.getById(recId)!!
        assertEquals(false, loaded.isFavorite)

        // Set favorite = true
        recordingDao.setFavorite(recId, true)
        loaded = recordingDao.getById(recId)!!
        assertEquals(true, loaded.isFavorite)

        val favorites = recordingDao.observeFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("Important Speech", favorites[0].displayName)

        // Set favorite = false
        recordingDao.setFavorite(recId, false)
        val favoritesEmpty = recordingDao.observeFavorites().first()
        assertTrue(favoritesEmpty.isEmpty())
    }

    @Test
    fun folderDeletionDoesNotDeleteRecordings() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "Client Calls"))
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "REC_CALL.m4a",
                displayName = "Client Call 1",
                filePath = "/path/CALL.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 45000L,
                fileSizeBytes = 200000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                folderId = folderId
            )
        )

        // Ensure recording is in folder
        assertEquals(folderId, recordingDao.getById(recId)?.folderId)

        // Clear folderId for recordings in this folder and delete folder
        recordingDao.clearFolderIdForRecordings(folderId)
        folderDao.deleteById(folderId)

        // Recording must STILL exist, but folderId must be null
        val preservedRecording = recordingDao.getById(recId)
        assertNotNull(preservedRecording)
        assertNull(preservedRecording?.folderId)
    }

    @Test
    fun tagDeletionDoesNotDeleteRecordings() = runTest {
        val tagId = tagDao.insert(TagEntity(name = "Review"))
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "REC_REV.m4a",
                displayName = "Review Audio",
                filePath = "/path/REV.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 15000L,
                fileSizeBytes = 50000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1
            )
        )
        tagDao.insertCrossRef(RecordingTagCrossRef(recordingId = recId, tagId = tagId))

        // Cross refs exist
        assertEquals(1, tagDao.observeTagsForRecording(recId).first().size)

        // Delete tag cross refs and delete tag
        tagDao.deleteCrossRefsForTag(tagId)
        val tagEntity = tagDao.getTagByName("Review")
        if (tagEntity != null) {
            tagDao.delete(tagEntity)
        }

        // Recording must STILL exist in database
        val recStillExists = recordingDao.getById(recId)
        assertNotNull(recStillExists)
        // Tag relationships are gone
        assertTrue(tagDao.observeTagsForRecording(recId).first().isEmpty())
    }

    @Test
    fun renameRecordingDisplayNameOnly() = runTest {
        val originalPath = "/original/path/audio.m4a"
        val recId = recordingDao.insert(
            RecordingEntity(
                fileName = "audio.m4a",
                displayName = "Original Name",
                filePath = originalPath,
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 10000L,
                fileSizeBytes = 40000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                createdAt = 5000L
            )
        )

        val updatedTime = 12000L
        recordingDao.renameRecording(recId, "Renamed Name", updatedTime)

        val retrieved = recordingDao.getById(recId)!!
        assertEquals("Renamed Name", retrieved.displayName)
        assertEquals(recId, retrieved.id)
        assertEquals(originalPath, retrieved.filePath)
        assertEquals("audio.m4a", retrieved.fileName)
        assertEquals(5000L, retrieved.createdAt)
        assertEquals(updatedTime, retrieved.modifiedAt)
    }

    @Test
    fun migration1to2ExecutesProperly() {
        // Test that migration SQL adds the isFavorite column successfully
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("test_migration.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS `recordings` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `fileName` TEXT NOT NULL,
                                    `displayName` TEXT NOT NULL,
                                    `filePath` TEXT NOT NULL,
                                    `mimeType` TEXT NOT NULL,
                                    `format` TEXT NOT NULL,
                                    `durationMs` INTEGER NOT NULL,
                                    `fileSizeBytes` INTEGER NOT NULL,
                                    `sampleRate` INTEGER NOT NULL,
                                    `bitRate` INTEGER NOT NULL,
                                    `channelCount` INTEGER NOT NULL,
                                    `folderId` INTEGER,
                                    `createdAt` INTEGER NOT NULL,
                                    `modifiedAt` INTEGER NOT NULL
                                )
                            """.trimIndent())
                        }
                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )

        val writableDb = dbHelper.writableDatabase
        // Run MIGRATION_1_2
        RecovoDatabase.MIGRATION_1_2.migrate(writableDb)

        // Verify column isFavorite was added by inserting a row with isFavorite
        writableDb.execSQL(
            "INSERT INTO recordings (fileName, displayName, filePath, mimeType, format, durationMs, fileSizeBytes, sampleRate, bitRate, channelCount, createdAt, modifiedAt, isFavorite) VALUES ('test.m4a', 'Test', '/p/test.m4a', 'audio/mp4', 'M4A', 1000, 1000, 44100, 128000, 1, 100, 100, 1)"
        )
        val cursor = writableDb.query("SELECT isFavorite FROM recordings WHERE fileName = 'test.m4a'")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        writableDb.close()
        context.deleteDatabase("test_migration.db")
    }
}
