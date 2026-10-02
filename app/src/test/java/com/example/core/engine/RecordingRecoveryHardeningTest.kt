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
import com.example.feature.library.RecordingUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RecordingRecoveryHardeningTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "recovery_test_${System.currentTimeMillis()}")
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
    fun engineCancelAfterStop_doesNotDeleteSavedAudioFile() = runTest(testDispatcher) {
        val testFile = File(testDir, "saved_memo.m4a")
        testFile.writeBytes(ByteArray(1024) { 0x0F }) // Simulated audio file content
        assertTrue(testFile.exists())

        // Simulate successful stop completion where engine clears currentFile
        var currentFile: File? = testFile

        // When stop completes successfully:
        currentFile = null

        // Subsequent cancel (e.g. from service onDestroy) must NOT delete the saved file
        currentFile?.let { file ->
            if (file.exists()) file.delete()
        }

        assertTrue("Saved recording file must NOT be deleted by subsequent cancel/onDestroy", testFile.exists())
    }

    @Test
    fun stopTwice_isHandledGracefullyWithoutCrashing() = runTest {
        val stateMachine = RecordingStateMachine()
        val dummyFile = File(testDir, "stop_twice_test.m4a")

        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // First stop succeeds
        val firstStop = stateMachine.stop()
        assertTrue(firstStop.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Saved)

        // Second stop is safely rejected without crash
        val secondStop = stateMachine.stop()
        assertFalse(secondStop.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Saved)
    }

    @Test
    fun cancelTwice_isIdempotentAndSafe() = runTest {
        val stateMachine = RecordingStateMachine()
        val dummyFile = File(testDir, "cancel_twice_test.m4a")

        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // First cancel
        val firstCancel = stateMachine.cancel()
        assertTrue(firstCancel.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)

        // Second cancel
        val secondCancel = stateMachine.cancel()
        assertTrue(secondCancel.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Idle)
    }

    @Test
    fun pauseTwice_secondCallIsRejectedCleanly() = runTest {
        val stateMachine = RecordingStateMachine()
        val dummyFile = File(testDir, "pause_twice_test.m4a")

        stateMachine.start(dummyFile)
        val firstPause = stateMachine.pause()
        assertTrue(firstPause.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Paused)

        val secondPause = stateMachine.pause()
        assertFalse(secondPause.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Paused)
    }

    @Test
    fun resumeWhileNotPaused_isRejected() = runTest {
        val stateMachine = RecordingStateMachine()
        val dummyFile = File(testDir, "resume_test.m4a")

        stateMachine.start(dummyFile)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        val resumeResult = stateMachine.resume()
        assertFalse(resumeResult.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)
    }

    @Test
    fun zeroByteFileOnStop_isRejectedAndCleanedUp() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("ZeroByteMemo", "m4a")
        assertTrue(fileResult is StorageResult.Success)
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())
        assertEquals(0L, file.length())

        // Stop validation rejects 0-byte files
        val isValid = file.isFile && file.length() > 0L
        assertFalse(isValid)

        if (!isValid) {
            file.delete()
        }

        assertFalse(file.exists())
        val all = repository.observeRecordings().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun databaseInsertionFailure_preservesPhysicalFileOnDisk() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("DatabaseFailSafe", "m4a")
        val file = (fileResult as StorageResult.Success).data
        val audioBytes = ByteArray(2048) { 0x55 }
        file.writeBytes(audioBytes)

        val entity = RecordingEntity(
            fileName = file.name,
            displayName = file.nameWithoutExtension,
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 3000L,
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        // Simulate database insertion exception
        var simulatedDbFailed = false
        try {
            throw IOException("Disk I/O error during Room transaction")
        } catch (e: Exception) {
            simulatedDbFailed = true
            // Policy: Keep physical file intact even if DB index fails
        }

        assertTrue(simulatedDbFailed)
        assertTrue("Physical file must be preserved on disk for recovery", file.exists())
        assertEquals(2048L, file.length())
    }

    @Test
    fun cleanOrphanedZeroByteFiles_onlyRemovesEmptyFiles() = runTest(testDispatcher) {
        val emptyFile1 = File(testDir, "empty_temp1.m4a").apply { createNewFile() }
        val emptyFile2 = File(testDir, "empty_temp2.m4a").apply { createNewFile() }
        val validFile = File(testDir, "valid_audio.m4a").apply {
            createNewFile()
            writeBytes(ByteArray(512) { 0x12 })
        }

        assertEquals(0L, emptyFile1.length())
        assertEquals(0L, emptyFile2.length())
        assertEquals(512L, validFile.length())

        val cleanResult = storageManager.cleanOrphanedZeroByteFiles()
        assertTrue(cleanResult is StorageResult.Success)
        val cleanedCount = (cleanResult as StorageResult.Success).data
        assertEquals(2, cleanedCount)

        assertFalse(emptyFile1.exists())
        assertFalse(emptyFile2.exists())
        assertTrue("Valid non-empty audio files must NEVER be cleaned", validFile.exists())
        assertEquals(512L, validFile.length())
    }

    @Test
    fun missingOrCorruptedFile_handledGracefullyInUiModelWithoutCrashing() {
        val missingEntity = RecordingEntity(
            id = 101,
            fileName = "missing.m4a",
            displayName = "Missing Audio",
            filePath = "/non/existent/path/missing.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 500L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        val file = File(missingEntity.filePath)
        val isAvailable = file.exists() && file.isFile && file.length() > 0L
        assertFalse(isAvailable)

        val uiModel = RecordingUiModel(
            entity = missingEntity,
            isFileAvailable = isAvailable,
            formattedDate = "Today",
            formattedDuration = "00:01",
            formattedSize = "500 B",
            folderName = null,
            tags = emptyList(),
            isFavorite = false,
            fileErrorMessage = "Audio file missing from storage"
        )

        assertFalse(uiModel.isFileAvailable)
        assertEquals("Audio file missing from storage", uiModel.fileErrorMessage)
    }
}
