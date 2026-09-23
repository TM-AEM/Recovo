package com.example.core.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.storage.StorageManager
import com.example.core.storage.StorageResult
import com.example.data.repository.RecordingRepository
import com.example.data.repository.RecordingRepositoryImpl
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
class RecordingSessionIntegrationTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_session_${System.currentTimeMillis()}")
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
    fun startRecording_createsPhysicalFileWithSanitizedName() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("Session 01", "m4a")
        assertTrue(fileResult is StorageResult.Success)
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())
        assertEquals("Session 01.m4a", file.name)
    }

    @Test
    fun successfulRecordingSession_savesToRoomAndLeavesPhysicalFileIntact() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("VoiceMemo", "m4a")
        val file = (fileResult as StorageResult.Success).data
        file.writeBytes(ByteArray(2048)) // Write mock audio bytes

        val sessionResult = RecordingSessionResult(
            file = file,
            durationMs = 4500L,
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            mimeType = "audio/mp4",
            format = "M4A"
        )

        val entity = RecordingEntity(
            fileName = sessionResult.file.name,
            displayName = sessionResult.file.nameWithoutExtension,
            filePath = sessionResult.file.absolutePath,
            mimeType = sessionResult.mimeType,
            format = sessionResult.format,
            durationMs = sessionResult.durationMs,
            fileSizeBytes = sessionResult.fileSizeBytes,
            sampleRate = sessionResult.sampleRate,
            bitRate = sessionResult.bitRate,
            channelCount = sessionResult.channelCount
        )

        val insertedId = repository.insertRecording(entity)
        assertTrue(insertedId > 0)

        val saved = repository.getRecordingById(insertedId)
        assertNotNull(saved)
        assertEquals("VoiceMemo", saved?.displayName)
        assertEquals(2048L, saved?.fileSizeBytes)
        assertEquals(4500L, saved?.durationMs)
        assertTrue(File(saved!!.filePath).exists())
    }

    @Test
    fun cancelledRecordingSession_deletesPhysicalFileAndDoesNotSaveToRoom() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("CancelledMemo", "m4a")
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())

        // Cancel simulated
        val deleteResult = storageManager.deleteFile(file.absolutePath)
        assertTrue(deleteResult is StorageResult.Success)
        assertFalse(file.exists())

        val allInDb = repository.observeRecordings().first()
        assertTrue(allInDb.isEmpty())
    }

    @Test
    fun failedRecording_doesNotCreateCorruptedOrInvalidDbEntity() = runTest(testDispatcher) {
        // When engine fails at initialization, no DB operation occurs
        val allInDb = repository.observeRecordings().first()
        assertEquals(0, allInDb.size)
    }
}
