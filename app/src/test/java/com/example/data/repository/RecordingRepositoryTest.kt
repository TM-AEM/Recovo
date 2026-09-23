package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.storage.StorageManager
import com.example.core.storage.StorageResult
import com.example.domain.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordingRepositoryTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_repo_${System.currentTimeMillis()}")
        testDir.mkdirs()

        database = Room.inMemoryDatabaseBuilder(context, RecovoDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        storageManager = StorageManager(
            context = context,
            baseDirectory = testDir,
            ioDispatcher = testDispatcher
        )

        repository = RecordingRepositoryImpl(
            recordingDao = database.recordingDao(),
            folderDao = database.folderDao(),
            tagDao = database.tagDao(),
            bookmarkDao = database.bookmarkDao(),
            storageManager = storageManager,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
        testDir.deleteRecursively()
    }

    @Test
    fun insertAndObserveRecordings() = runTest(testDispatcher) {
        val recording = RecordingEntity(
            fileName = "REC_01.m4a",
            displayName = "Voice Memo 1",
            filePath = "/fake/path/REC_01.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 12000L,
            fileSizeBytes = 50000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        val id = repository.insertRecording(recording)
        assertTrue(id > 0)

        val retrieved = repository.getRecordingById(id)
        assertNotNull(retrieved)
        assertEquals("Voice Memo 1", retrieved?.displayName)

        val list = repository.observeRecordings(SortOrder.NEWEST).first()
        assertEquals(1, list.size)
    }

    @Test
    fun deleteRecording_removesFromDbAndDeletesPhysicalFile() = runTest(testDispatcher) {
        // Create actual file via storage manager
        val fileResult = storageManager.createRecordingFile(desiredName = "MemoToDelete", extension = "m4a")
        assertTrue(fileResult is StorageResult.Success)
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())

        // Insert entity pointing to that file
        val recording = RecordingEntity(
            fileName = file.name,
            displayName = "Memo To Delete",
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val id = repository.insertRecording(recording)
        val inserted = repository.getRecordingById(id)!!

        // Execute delete
        val deleteResult = repository.deleteRecording(inserted)
        assertTrue(deleteResult is StorageResult.Success)

        // Verify removed from database
        assertNull(repository.getRecordingById(id))

        // Verify physical file was deleted
        assertFalse(file.exists())
    }

    @Test
    fun foldersAndBookmarksOperations() = runTest(testDispatcher) {
        val folderId = repository.createFolder("Work")
        assertTrue(folderId > 0)

        val folders = repository.observeFolders().first()
        assertEquals(1, folders.size)
        assertEquals("Work", folders[0].name)

        val recId = repository.insertRecording(
            RecordingEntity(
                fileName = "REC_W.m4a",
                displayName = "Work Memo",
                filePath = "/dummy.m4a",
                mimeType = "audio/mp4",
                format = "M4A",
                durationMs = 8000L,
                fileSizeBytes = 4000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                folderId = folderId
            )
        )

        val bmId = repository.addBookmark(
            recordingId = recId,
            positionMs = 2500L,
            label = "Topic Intro"
        )
        assertTrue(bmId > 0)

        val bookmarks = repository.getBookmarks(recId)
        assertEquals(1, bookmarks.size)
        assertEquals("Topic Intro", bookmarks[0].label)
    }
}
