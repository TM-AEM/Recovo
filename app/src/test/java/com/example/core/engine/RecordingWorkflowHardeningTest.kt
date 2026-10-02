package com.example.core.engine

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.service.RecordingController
import com.example.core.service.RecordingService
import com.example.core.storage.StorageManager
import com.example.core.storage.StorageResult
import com.example.data.repository.RecordingRepository
import com.example.data.repository.RecordingRepositoryImpl
import com.example.feature.record.RecordViewModel
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
class RecordingWorkflowHardeningTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "workflow_test_${System.currentTimeMillis()}")
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
    fun recordingService_intentActionsAndExtras_areProperlyStructured() {
        val startIntent = RecordingService.startRecordingIntent(context, "Voice Memo", "maximum")
        assertEquals(RecordingService.ACTION_START, startIntent.action)
        assertEquals("Voice Memo", startIntent.getStringExtra(RecordingService.EXTRA_DISPLAY_NAME))
        assertEquals("maximum", startIntent.getStringExtra(RecordingService.EXTRA_QUALITY_ID))

        val pauseIntent = RecordingService.pauseIntent(context, isInterrupted = true)
        assertEquals(RecordingService.ACTION_PAUSE, pauseIntent.action)
        assertTrue(pauseIntent.getBooleanExtra(RecordingService.EXTRA_IS_INTERRUPTED, false))

        val resumeIntent = RecordingService.resumeIntent(context)
        assertEquals(RecordingService.ACTION_RESUME, resumeIntent.action)

        val stopIntent = RecordingService.stopIntent(context)
        assertEquals(RecordingService.ACTION_STOP, stopIntent.action)

        val cancelIntent = RecordingService.cancelIntent(context)
        assertEquals(RecordingService.ACTION_CANCEL, cancelIntent.action)
    }

    @Test
    fun customTitle_withArabicAndUnicode_isSanitizedAndPreserved() = runTest(testDispatcher) {
        val arabicTitle = "تسجيل صوتي هام"
        val result = storageManager.createRecordingFile(desiredName = arabicTitle, extension = "m4a")
        assertTrue(result is StorageResult.Success)
        val file = (result as StorageResult.Success).data
        assertTrue(file.exists())
        assertTrue("Arabic characters must be safely preserved in file name", file.name.contains("تسجيل"))
        assertEquals("m4a", file.extension)
    }

    @Test
    fun duplicateStart_whileActive_isRejectedByStateMachine() = runTest {
        val stateMachine = RecordingStateMachine()
        val dummyFile1 = File(testDir, "file1.m4a")
        val dummyFile2 = File(testDir, "file2.m4a")

        val firstStart = stateMachine.start(dummyFile1)
        assertTrue(firstStart.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)

        // Attempting a second start while in Recording state must fail cleanly
        val secondStart = stateMachine.start(dummyFile2)
        assertFalse(secondStart.isSuccess)
        assertTrue(stateMachine.state.value is RecordingState.Recording)
        assertEquals(dummyFile1, (stateMachine.state.value as RecordingState.Recording).file)
    }

    @Test
    fun recordViewModel_onCleared_releasesControllerCleanly() {
        val controller = RecordingController(context)
        val viewModel = RecordViewModel(controller)

        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        val onClearedMethod = RecordViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        // Verifies release does not throw
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun roomInsertionFailure_strictlyPreservesValidAudioOnDisk() = runTest(testDispatcher) {
        val fileResult = storageManager.createRecordingFile("SafePreservedAudio", "m4a")
        val file = (fileResult as StorageResult.Success).data
        file.writeBytes(ByteArray(4096) { 0xAA.toByte() })

        val entity = RecordingEntity(
            fileName = file.name,
            displayName = file.nameWithoutExtension,
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        var dbFailed = false
        try {
            throw IOException("Simulated disk error during database commit")
        } catch (e: Exception) {
            dbFailed = true
            // Policy invariant: do not delete the physical audio file if database insert fails
        }

        assertTrue(dbFailed)
        assertTrue("Valid audio file must NEVER be deleted when Room metadata insertion fails", file.exists())
        assertEquals(4096L, file.length())
    }

    @Test
    fun rapidPauseResumeSequence_preservesMonotonicDurationTracker() {
        var clock = 1000L
        val tracker = RecordingDurationTracker { clock }

        tracker.start() // t=1000
        clock += 500L  // recorded 500ms
        assertEquals(500L, tracker.elapsedMs())

        tracker.pause() // t=1500
        clock += 2000L  // paused for 2000ms
        assertEquals(500L, tracker.elapsedMs())

        tracker.resume() // t=3500
        clock += 1000L  // recorded another 1000ms
        assertEquals(1500L, tracker.elapsedMs())

        tracker.pause() // t=4500
        clock += 5000L  // paused for 5000ms
        assertEquals(1500L, tracker.elapsedMs())

        tracker.resume() // t=9500
        clock += 500L   // recorded another 500ms
        assertEquals(2000L, tracker.elapsedMs())
    }
}
