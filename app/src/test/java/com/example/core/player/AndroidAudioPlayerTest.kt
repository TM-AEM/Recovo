package com.example.core.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.model.RecordingEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAudioPlayerTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = AndroidAudioPlayer(context, testScope)
    }

    @Test
    fun initialState_isIdle() {
        val state = player.playbackState.value
        assertNull(state.currentRecording)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertNull(state.errorMessage)
    }

    @Test
    fun play_nonExistentFile_setsErrorMessage() {
        val recording = RecordingEntity(
            id = 1,
            fileName = "missing.m4a",
            displayName = "Missing File",
            filePath = "/invalid/nonexistent/missing.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        player.play(recording)

        val state = player.playbackState.value
        assertFalse(state.isPlaying)
        assertNotNull(state.errorMessage)
        assertEquals("Audio file missing from storage", state.errorMessage)

        player.clearError()
        assertNull(player.playbackState.value.errorMessage)
    }

    @Test
    fun play_emptyZeroByteFile_setsErrorMessage() {
        val emptyFile = File.createTempFile("empty", ".m4a", context.cacheDir)
        emptyFile.deleteOnExit()

        val recording = RecordingEntity(
            id = 2,
            fileName = emptyFile.name,
            displayName = "Empty File",
            filePath = emptyFile.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 0L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        player.play(recording)

        val state = player.playbackState.value
        assertFalse(state.isPlaying)
        assertNotNull(state.errorMessage)
        assertEquals("Audio file is corrupted or empty (0 bytes)", state.errorMessage)

        emptyFile.delete()
    }

    @Test
    fun stopAndRelease_resetsState() {
        player.stop()
        val state = player.playbackState.value
        assertNull(state.currentRecording)
        assertFalse(state.isPlaying)

        player.release()
        val finalState = player.playbackState.value
        assertNull(finalState.currentRecording)
    }

    @Test
    fun seek_clampsWithinDuration() {
        player.seekTo(-500L)
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        player.seekRelative(1000L)
        // With duration 0, clamped to 0
        assertEquals(0L, player.playbackState.value.currentPositionMs)
    }

    @Test
    fun playlist_nextAndPreviousNavigation() {
        val rec1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "One",
            filePath = "/nonexistent/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val rec2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "Two",
            filePath = "/nonexistent/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 8000L,
            fileSizeBytes = 2000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        player.setPlaylist(listOf(rec1, rec2))

        // When nothing is playing, playNext / playPrevious does not crash
        player.playNext()
        player.playPrevious()
        assertNull(player.playbackState.value.currentRecording)
    }

    @Test
    fun setSpeed_clampsAndStoresCorrectly() {
        // Supported normal speed
        player.setSpeed(1.5f)
        assertEquals(1.5f, player.playbackState.value.playbackSpeed, 0.01f)

        // Upper clamp
        player.setSpeed(5.0f)
        assertEquals(2.0f, player.playbackState.value.playbackSpeed, 0.01f)

        // Lower clamp
        player.setSpeed(0.1f)
        assertEquals(0.25f, player.playbackState.value.playbackSpeed, 0.01f)

        // Temporary 2x boost & restore
        player.setSpeed(1.25f)
        player.startTemporaryFastForward()
        assertEquals(2.0f, player.playbackState.value.playbackSpeed, 0.01f)

        player.stopTemporaryFastForward()
        assertEquals(1.25f, player.playbackState.value.playbackSpeed, 0.01f)
    }

    @Test
    fun repeatMode_setsAndCycles() {
        assertEquals(RepeatMode.OFF, player.playbackState.value.repeatMode)

        player.setRepeatMode(RepeatMode.ALL)
        assertEquals(RepeatMode.ALL, player.playbackState.value.repeatMode)

        player.toggleRepeatMode()
        assertEquals(RepeatMode.ONE, player.playbackState.value.repeatMode)

        player.toggleRepeatMode()
        assertEquals(RepeatMode.OFF, player.playbackState.value.repeatMode)
    }

    @Test
    fun shuffle_togglingWithPlaylist() {
        val rec1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "One",
            filePath = "/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val rec2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "Two",
            filePath = "/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 8000L,
            fileSizeBytes = 2000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        player.setPlaylist(listOf(rec1, rec2))
        assertFalse(player.playbackState.value.isShuffleEnabled)

        player.toggleShuffle()
        assertTrue(player.playbackState.value.isShuffleEnabled)

        player.toggleShuffle()
        assertFalse(player.playbackState.value.isShuffleEnabled)
    }

    @Test
    fun sleepTimer_setsAndCancels() {
        assertNull(player.playbackState.value.sleepTimerRemainingMs)

        player.setSleepTimer(30)
        assertEquals(30 * 60 * 1000L, player.playbackState.value.sleepTimerRemainingMs)

        player.cancelSleepTimer()
        assertNull(player.playbackState.value.sleepTimerRemainingMs)

        player.setSleepTimer(10)
        player.stop()
        assertNull(player.playbackState.value.sleepTimerRemainingMs)
    }

    @Test
    fun updateCurrentRecordingMetadata_updatesStateIfActive() {
        val rec = RecordingEntity(
            id = 5,
            fileName = "five.m4a",
            displayName = "Original Name",
            filePath = "/fake/five.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 10000L,
            fileSizeBytes = 500L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        player.play(rec)
        assertEquals("Original Name", player.playbackState.value.currentRecording?.displayName)

        val updated = rec.copy(displayName = "Updated Renamed Title")
        player.updateCurrentRecordingMetadata(updated)
        assertEquals("Updated Renamed Title", player.playbackState.value.currentRecording?.displayName)
    }
}
