package com.tmaem.recovo.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.dao.BookmarkDao
import com.tmaem.recovo.core.database.dao.FolderDao
import com.tmaem.recovo.core.database.dao.RecordingDao
import com.tmaem.recovo.core.database.dao.TagDao
import com.tmaem.recovo.core.database.model.BookmarkEntity
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity
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
            filePath = "/data/user/0/com.tmaem.recovo/files/recordings/REC_001.m4a",
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

    // --- Resume position (schema v3) ---

    @Test
    fun saveAndReadResumePositionById() = runTest {
        val id = recordingDao.insert(
            RecordingEntity(
                fileName = "resume.m4a",
                displayName = "Resume",
                filePath = "/p/resume.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 60_000L,
                fileSizeBytes = 1_000L,
                sampleRate = 44_100,
                bitRate = 128_000,
                channelCount = 1
            )
        )
        assertNull(recordingDao.getById(id)!!.resumePositionMs)

        recordingDao.setResumePosition(id, 12_345L)
        assertEquals(12_345L, recordingDao.getById(id)!!.resumePositionMs)

        // Clearing stores null again.
        recordingDao.setResumePosition(id, null)
        assertNull(recordingDao.getById(id)!!.resumePositionMs)
    }

    @Test
    fun setResumePosition_doesNotOverwriteOtherFields() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "Work"))
        val tagId = tagDao.insert(TagEntity(name = "meeting"))
        val id = recordingDao.insert(
            RecordingEntity(
                fileName = "keep.m4a",
                displayName = "Keep My Metadata",
                filePath = "/p/keep.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 90_000L,
                fileSizeBytes = 2_000L,
                sampleRate = 48_000,
                bitRate = 192_000,
                channelCount = 2,
                folderId = folderId,
                isFavorite = true,
                createdAt = 111L,
                modifiedAt = 222L
            )
        )
        tagDao.insertCrossRef(RecordingTagCrossRef(id, tagId))
        bookmarkDao.insert(BookmarkEntity(recordingId = id, positionMs = 5_000L, label = "Intro", createdAt = 1L))

        recordingDao.setResumePosition(id, 33_000L)

        val after = recordingDao.getById(id)!!
        assertEquals("Keep My Metadata", after.displayName)
        assertEquals(folderId, after.folderId)
        assertTrue(after.isFavorite)
        assertEquals(90_000L, after.durationMs)
        assertEquals(2_000L, after.fileSizeBytes)
        assertEquals(48_000, after.sampleRate)
        assertEquals(192_000, after.bitRate)
        assertEquals(2, after.channelCount)
        assertEquals(111L, after.createdAt)
        assertEquals(222L, after.modifiedAt)
        assertEquals(33_000L, after.resumePositionMs)

        // Tags and bookmarks are untouched.
        assertEquals(listOf(tagId), tagDao.getTagsForRecording(id).map { it.id })
        assertEquals(1, bookmarkDao.getBookmarksForRecording(id).size)
        assertEquals(5_000L, bookmarkDao.getBookmarksForRecording(id).first().positionMs)
    }

    @Test
    fun setResumePosition_nonexistentId_doesNotInsertRow() = runTest {
        recordingDao.setResumePosition(9_999L, 1_000L)
        assertNull(recordingDao.getById(9_999L))
        assertEquals(0, recordingDao.getById(9_999L)?.let { 1 } ?: 0)
    }

    @Test
    fun updateDuration_changesOnlyDurationAndModifiedAt() = runTest {
        val folderId = folderDao.insert(FolderEntity(name = "Work"))
        val tagId = tagDao.insert(TagEntity(name = "meeting"))
        val id = recordingDao.insert(
            RecordingEntity(
                fileName = "dur.m4a",
                displayName = "Duration Target",
                filePath = "/p/dur.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 90_000L,
                fileSizeBytes = 2_000L,
                sampleRate = 48_000,
                bitRate = 192_000,
                channelCount = 2,
                folderId = folderId,
                isFavorite = true,
                createdAt = 111L,
                modifiedAt = 222L
            )
        )
        tagDao.insertCrossRef(RecordingTagCrossRef(id, tagId))
        bookmarkDao.insert(BookmarkEntity(recordingId = id, positionMs = 5_000L, label = "Intro", createdAt = 1L))
        recordingDao.setResumePosition(id, 33_000L)

        recordingDao.updateDuration(id, 77_000L, 999L)

        val after = recordingDao.getById(id)!!
        // Only the duration-related columns change.
        assertEquals(77_000L, after.durationMs)
        assertEquals(999L, after.modifiedAt)
        // A newer resume position is preserved, never clobbered by the duration write.
        assertEquals(33_000L, after.resumePositionMs)
        // All unrelated fields are untouched.
        assertEquals("Duration Target", after.displayName)
        assertEquals(folderId, after.folderId)
        assertTrue(after.isFavorite)
        assertEquals(2_000L, after.fileSizeBytes)
        assertEquals(48_000, after.sampleRate)
        assertEquals(192_000, after.bitRate)
        assertEquals(2, after.channelCount)
        assertEquals(111L, after.createdAt)

        assertEquals(listOf(tagId), tagDao.getTagsForRecording(id).map { it.id })
        assertEquals(1, bookmarkDao.getBookmarksForRecording(id).size)
        assertEquals(5_000L, bookmarkDao.getBookmarksForRecording(id).first().positionMs)
    }

    @Test
    fun updateDuration_nonexistentId_doesNotInsertRow() = runTest {
        recordingDao.updateDuration(9_999L, 1_000L, 1L)
        assertNull(recordingDao.getById(9_999L))
    }


    @Test
    fun migration2to3_addsNullableColumn_preservingExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_2_3.db"
        context.deleteDatabase(dbName)

        // Build a genuine v2 database (version 2) with the exact v2 schema, then insert a row.
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL)")
                            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `displayName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `format` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `sampleRate` INTEGER NOT NULL, `bitRate` INTEGER NOT NULL, `channelCount` INTEGER NOT NULL, `folderId` INTEGER, `isFavorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_folderId` ON `recordings` (`folderId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_createdAt` ON `recordings` (`createdAt`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_displayName` ON `recordings` (`displayName`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_isFavorite` ON `recordings` (`isFavorite`)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS `recording_tag_cross_ref` (`recordingId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`recordingId`, `tagId`), FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_tag_cross_ref_recordingId` ON `recording_tag_cross_ref` (`recordingId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_tag_cross_ref_tagId` ON `recording_tag_cross_ref` (`tagId`)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordingId` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_recordingId` ON `bookmarks` (`recordingId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_positionMs` ON `bookmarks` (`positionMs`)")
                            db.execSQL("INSERT INTO folders (id, name, createdAt, modifiedAt) VALUES (1, 'Work', 10, 20)")
                            db.execSQL("INSERT INTO recordings (id, fileName, displayName, filePath, mimeType, format, durationMs, fileSizeBytes, sampleRate, bitRate, channelCount, folderId, isFavorite, createdAt, modifiedAt) VALUES (7, 'v2.m4a', 'V2 Row', '/p/v2.m4a', 'audio/mp4', 'M4A', 60000, 2048, 44100, 128000, 2, 1, 1, 111, 222)")
                            db.execSQL("INSERT INTO tags (id, name, createdAt) VALUES (1, 'meeting', 30)")
                            db.execSQL("INSERT INTO recording_tag_cross_ref (recordingId, tagId) VALUES (7, 1)")
                            db.execSQL("INSERT INTO bookmarks (id, recordingId, positionMs, label, createdAt) VALUES (1, 7, 5000, 'Intro', 40)")
                        }

                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )

        val db = helper.writableDatabase
        RecovoDatabase.MIGRATION_2_3.migrate(db)

        // The new column exists and is initially null for the pre-existing row.
        db.query("SELECT resumePositionMs FROM recordings WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        // Existing metadata survives.
        db.query("SELECT displayName, folderId, isFavorite, durationMs FROM recordings WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("V2 Row", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(1, c.getInt(2))
            assertEquals(60_000L, c.getLong(3))
        }
        // Related tables survive.
        db.query("SELECT COUNT(*) FROM recording_tag_cross_ref WHERE recordingId = 7").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bookmarks WHERE recordingId = 7").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.close()
        context.deleteDatabase(dbName)
    }
}
