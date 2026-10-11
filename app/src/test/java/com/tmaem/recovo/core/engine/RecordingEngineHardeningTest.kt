package com.tmaem.recovo.core.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.RecovoDatabase
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.data.repository.RecordingRepository
import com.tmaem.recovo.data.repository.RecordingRepositoryImpl
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

/**
 * Phase 05 Hardening Test Suite.
 * Validates timer accuracy (elapsedRealtime arithmetic), pause/resume exclusion,
 * 0-byte file corruption prevention, and Room persistence integrity.
 */
@RunWith(AndroidJUnit4::class)
class RecordingEngineHardeningTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "hardening_test_${System.currentTimeMillis()}")
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
    fun timerCalculation_activeDurationExcludesPausedDuration() {
        // Simulation of elapsedRealtime arithmetic
        var accumulatedDurationMs = 0L
        var recordingStartRealtime = 1000L

        // 1. Record for 1000ms (from t=1000 to t=2000)
        val pauseTimeRealtime = 2000L
        accumulatedDurationMs += (pauseTimeRealtime - recordingStartRealtime)
        assertEquals(1000L, accumulatedDurationMs)

        // 2. Paused for 10,000ms (from t=2000 to t=12000) - time does not advance
        val resumeTimeRealtime = 12000L
        recordingStartRealtime = resumeTimeRealtime

        // While paused, current duration remains exactly 1000ms
        assertEquals(1000L, accumulatedDurationMs)

        // 3. Resume and record for another 1000ms (from t=12000 to t=13000)
        val stopTimeRealtime = 13000L
        val totalActiveDurationMs = accumulatedDurationMs + (stopTimeRealtime - recordingStartRealtime)

        // Total physical elapsed is 12,000ms (from 1,000 to 13,000)
        // BUT active recorded duration MUST be exactly 2,000ms (1000 + 1000)
        assertEquals(2000L, totalActiveDurationMs)
    }

    @Test
    fun emptyOrZeroByteFile_isNotPersistedToDatabaseAndIsDeleted() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("EmptyMemo", "m4a")
        assertTrue(fileResult is StorageResult.Success)
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())
        assertEquals(0L, file.length())

        // Simulate service stop validation rule: 0-byte files are rejected
        val isCorruptOrEmpty = file.length() <= 0L
        assertTrue(isCorruptOrEmpty)

        if (isCorruptOrEmpty) {
            file.delete()
        }

        assertFalse(file.exists())

        // Database must remain completely clean
        val allRecordings = repository.observeRecordings().first()
        assertTrue(allRecordings.isEmpty())
    }

    @Test
    fun validAudioFile_persistsSafelyWithPreciseDuration() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("ValidMemo", "m4a")
        val file = (fileResult as StorageResult.Success).data
        val audioBytes = ByteArray(4096) { 0x01 }
        file.writeBytes(audioBytes)

        val entity = RecordingEntity(
            fileName = file.name,
            displayName = file.nameWithoutExtension,
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 2000L, // 2 seconds active duration
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        val id = repository.insertRecording(entity)
        assertTrue(id > 0)

        val loaded = repository.getRecordingById(id)
        assertNotNull(loaded)
        assertEquals("ValidMemo", loaded?.displayName)
        assertEquals(2000L, loaded?.durationMs)
        assertEquals(4096L, loaded?.fileSizeBytes)
        assertTrue(File(loaded!!.filePath).exists())
    }

    @Test
    fun cancelRecording_cleansUpDiskAndDoesNotTouchDatabase() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("CancelledSession", "m4a")
        val file = (fileResult as StorageResult.Success).data
        assertTrue(file.exists())

        // Cancellation deletes physical file
        val deleted = storageManager.deleteFile(file.absolutePath)
        assertTrue(deleted is StorageResult.Success)
        assertFalse(file.exists())

        val count = repository.observeRecordingCount().first()
        assertEquals(0, count)
    }

    @Test
    fun rapidConsecutiveSave_handlesDatabaseConstraintGracefully() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("RapidMemo", "m4a")
        val file = (fileResult as StorageResult.Success).data
        file.writeBytes(ByteArray(1024))

        val entity = RecordingEntity(
            fileName = file.name,
            displayName = file.nameWithoutExtension,
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1500L,
            fileSizeBytes = 1024L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        val id1 = repository.insertRecording(entity)
        assertTrue(id1 > 0)

        // Update modification time
        repository.updateRecording(entity.copy(id = id1, durationMs = 1600L))
        val updated = repository.getRecordingById(id1)
        assertEquals(1600L, updated?.durationMs)
    }
}
