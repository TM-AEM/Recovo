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
}
