package com.tmaem.recovo.core.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.RecovoDatabase
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.engine.RecordingState
import com.tmaem.recovo.core.player.AndroidAudioPlayer
import com.tmaem.recovo.core.player.AudioPlayerProvider
import com.tmaem.recovo.core.service.RecordingService
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.data.repository.RecordingRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.robolectric.Robolectric
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecordingPlaybackIntegrationTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    private fun createValidTestAudioFile(name: String): File {
        val file = File(context.cacheDir, "$name.m4a")
        if (file.exists()) file.delete()
        file.writeBytes(ByteArray(1024) { 0x55.toByte() })
        val ds = org.robolectric.shadows.util.DataSource.toDataSource(file.absolutePath)
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(ds, org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(5000, 0))
        return file
    }

    private fun createDummyRecording(id: Long, file: File): RecordingEntity {
        return RecordingEntity(
            id = id,
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
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = AndroidAudioPlayer(context, testScope)
        AudioPlayerProvider.setForTesting(player)
        RecordingService.setRecordingActiveForTesting(false)
    }

    @After
    fun tearDown() {
        RecordingService.setRecordingActiveForTesting(false)
        player.stop()
        player.release()
        AudioPlayerProvider.setForTesting(null)
    }

    @Test
    fun attemptPlayback_whileRecordingActive_isRejectedWithError() {
        RecordingService.setRecordingActiveForTesting(true)

        val file = createValidTestAudioFile("test_reject")
        val rec = createDummyRecording(1L, file)

        player.play(rec)

        // Playback must not start and error message must indicate rejection
        assertFalse(player.playbackState.value.isPlaying)
        assertEquals("Cannot start playback while recording is in progress", player.playbackState.value.errorMessage)
        assertNull(player.playbackState.value.currentRecording)

        file.delete()
    }

    @Test
    fun attemptResume_whileRecordingActive_isRejectedWithError() {
        val file = createValidTestAudioFile("test_resume_reject")
        val rec = createDummyRecording(2L, file)

        // Initial play before recording
        player.play(rec)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        player.pause()

        // Recording becomes active
        RecordingService.setRecordingActiveForTesting(true)

        // Attempt resume
        player.resume()

        assertFalse(player.playbackState.value.isPlaying)
        assertEquals("Cannot start playback while recording is in progress", player.playbackState.value.errorMessage)

        file.delete()
    }

    @Test
    fun stopRecording_clearsActiveFlag_andAllowsPlayback() {
        val file = createValidTestAudioFile("test_allow")
        val rec = createDummyRecording(3L, file)

        // Simulate recording active
        RecordingService.setRecordingActiveForTesting(true)
        assertTrue(RecordingService.isRecordingActive())

        // Recording finishes
        RecordingService.setRecordingActiveForTesting(false)
        assertFalse(RecordingService.isRecordingActive())

        player.play(rec)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull(player.playbackState.value.errorMessage)

        file.delete()
    }

    @Test
    fun startRecording_inService_pausesPlaybackExplicitly() {
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        val file = createValidTestAudioFile("test_service_pause")
        val rec = createDummyRecording(4L, file)

        player.play(rec)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Trigger start recording on service
        service.startRecording("My Rec", null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Player must be paused
        assertFalse(player.playbackState.value.isPlaying)
        assertNull(player.playbackState.value.sleepTimerRemainingMs)

        serviceController.destroy()
        file.delete()
    }

    @Test
    fun newlySavedRecording_isFullyPlayableAfterStop() = runTest {
        val db = RecovoDatabase.getInstance(context)
        val storageManager = StorageManager(context)
        val repository = RecordingRepositoryImpl(
            recordingDao = db.recordingDao(),
            folderDao = db.folderDao(),
            tagDao = db.tagDao(),
            bookmarkDao = db.bookmarkDao(),
            storageManager = storageManager
        )

        val file = createValidTestAudioFile("new_saved_audio")
        val entity = RecordingEntity(
            fileName = file.name,
            displayName = "New Saved Audio",
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 6000L,
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        val id = repository.insertRecording(entity)
        assertTrue(id > 0L)

        val retrieved = repository.getRecordingById(id)
        assertNotNull(retrieved)

        // Playing the retrieved entity immediately succeeds
        player.play(retrieved!!)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull(player.playbackState.value.errorMessage)
        assertEquals("New Saved Audio", player.playbackState.value.currentRecording?.displayName)

        file.delete()
    }

    @Test
    fun rapidRecordThenPlay_preservesDeterministicState() {
        val file1 = createValidTestAudioFile("rapid1")
        val rec1 = createDummyRecording(10L, file1)

        // 1. Play
        player.play(rec1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // 2. Recording starts
        RecordingService.setRecordingActiveForTesting(true)
        player.pause()

        // 3. User attempts play during recording (rejected)
        player.play(rec1)
        assertFalse(player.playbackState.value.isPlaying)
        assertEquals("Cannot start playback while recording is in progress", player.playbackState.value.errorMessage)

        // 4. Recording stops
        RecordingService.setRecordingActiveForTesting(false)
        player.clearError()

        // 5. User plays again (succeeds)
        player.play(rec1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull(player.playbackState.value.errorMessage)

        file1.delete()
    }

    @Test
    fun playMissingFileWhilePlaying_tearsDownPreviousPlayerNoStaleState() {
        val fileA = createValidTestAudioFile("stale_missing_a")
        val recA = createDummyRecording(20L, fileA)
        player.play(recA)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)
        assertTrue(player.playbackState.value.durationMs > 0L)

        val missing = createDummyRecording(21L, java.io.File("/nonexistent/stale_missing.m4a"))
        player.play(missing)

        val state = player.playbackState.value
        // The previous track must not keep playing or leave stale duration/position behind.
        assertFalse(state.isPlaying)
        assertFalse(state.isPrepared)
        assertEquals(0L, state.durationMs)
        assertEquals(0L, state.currentPositionMs)
        assertEquals("Audio file missing from storage", state.errorMessage)
        assertEquals(21L, state.currentRecording?.id)
        fileA.delete()
    }

    @Test
    fun playZeroByteFileWhilePlaying_tearsDownPreviousPlayerNoStaleState() {
        val fileA = createValidTestAudioFile("stale_zero_a")
        val recA = createDummyRecording(30L, fileA)
        player.play(recA)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)

        val zeroFile = File(context.cacheDir, "stale_zero.m4a")
        if (zeroFile.exists()) zeroFile.delete()
        zeroFile.writeBytes(ByteArray(0))
        val zeroRec = createDummyRecording(31L, zeroFile)
        player.play(zeroRec)

        val state = player.playbackState.value
        assertFalse(state.isPlaying)
        assertFalse(state.isPrepared)
        assertEquals(0L, state.durationMs)
        assertEquals(0L, state.currentPositionMs)
        assertEquals("Audio file is corrupted or empty (0 bytes)", state.errorMessage)
        assertEquals(31L, state.currentRecording?.id)

        fileA.delete()
        zeroFile.delete()
    }
}
