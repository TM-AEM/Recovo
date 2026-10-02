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
class PlaybackSubsystemHardeningTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    private fun createDummyRecording(id: Long, name: String, duration: Long): RecordingEntity {
        return RecordingEntity(
            id = id,
            fileName = "$name.m4a",
            displayName = name,
            filePath = "/nonexistent/$name.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = duration,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = AndroidAudioPlayer(context, testScope)
    }

    @Test
    fun playlist_repeatModesCycleCorrectly() {
        assertEquals(RepeatMode.OFF, player.playbackState.value.repeatMode)

        player.toggleRepeatMode()
        assertEquals(RepeatMode.ALL, player.playbackState.value.repeatMode)

        player.toggleRepeatMode()
        assertEquals(RepeatMode.ONE, player.playbackState.value.repeatMode)

        player.toggleRepeatMode()
        assertEquals(RepeatMode.OFF, player.playbackState.value.repeatMode)
    }

    @Test
    fun playlist_shufflePreservesActiveTrackFirst() {
        val r1 = createDummyRecording(1, "Track 1", 3000L)
        val r2 = createDummyRecording(2, "Track 2", 4000L)
        val r3 = createDummyRecording(3, "Track 3", 5000L)

        player.setPlaylist(listOf(r1, r2, r3))
        assertFalse(player.playbackState.value.isShuffleEnabled)

        player.setShuffleEnabled(true)
        assertTrue(player.playbackState.value.isShuffleEnabled)

        player.setShuffleEnabled(false)
        assertFalse(player.playbackState.value.isShuffleEnabled)
    }

    @Test
    fun speed_clampRange_0_25_to_2_0() {
        player.setSpeed(0.1f)
        assertEquals(0.25f, player.playbackState.value.playbackSpeed, 0.001f)

        player.setSpeed(0.5f)
        assertEquals(0.5f, player.playbackState.value.playbackSpeed, 0.001f)

        player.setSpeed(1.0f)
        assertEquals(1.0f, player.playbackState.value.playbackSpeed, 0.001f)

        player.setSpeed(1.75f)
        assertEquals(1.75f, player.playbackState.value.playbackSpeed, 0.001f)

        player.setSpeed(2.0f)
        assertEquals(2.0f, player.playbackState.value.playbackSpeed, 0.001f)

        player.setSpeed(3.5f)
        assertEquals(2.0f, player.playbackState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun fastForward_temporaryBoost_restoresPreviousSpeed() {
        player.setSpeed(1.25f)
        assertEquals(1.25f, player.playbackState.value.playbackSpeed, 0.001f)

        player.startTemporaryFastForward()
        assertEquals(2.0f, player.playbackState.value.playbackSpeed, 0.001f)

        player.stopTemporaryFastForward()
        assertEquals(1.25f, player.playbackState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun sleepTimer_settingAndCancelling() {
        assertNull(player.playbackState.value.sleepTimerRemainingMs)

        player.setSleepTimer(10)
        assertEquals(10 * 60 * 1000L, player.playbackState.value.sleepTimerRemainingMs)

        player.cancelSleepTimer()
        assertNull(player.playbackState.value.sleepTimerRemainingMs)

        player.setSleepTimer(5)
        assertEquals(5 * 60 * 1000L, player.playbackState.value.sleepTimerRemainingMs)

        player.setSleepTimer(null)
        assertNull(player.playbackState.value.sleepTimerRemainingMs)
    }

    @Test
    fun seek_negativeAndBeyondDuration_clampsSafely() {
        player.seekTo(-1000L)
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        player.seekTo(100000L)
        // With duration 0, clamped to 0
        assertEquals(0L, player.playbackState.value.currentPositionMs)
    }

    @Test
    fun missingFile_reportsAccurateError() {
        val missing = createDummyRecording(99, "Ghost", 2000L)
        player.play(missing)

        assertEquals("Audio file missing from storage", player.playbackState.value.errorMessage)
        assertFalse(player.playbackState.value.isPlaying)

        player.clearError()
        assertNull(player.playbackState.value.errorMessage)
    }

    @Test
    fun zeroByteFile_reportsCorruptedError() {
        val temp = File.createTempFile("corrupt_test", ".m4a", context.cacheDir)
        temp.deleteOnExit()

        val rec = RecordingEntity(
            id = 101,
            fileName = temp.name,
            displayName = "Corrupt File",
            filePath = temp.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 3000L,
            fileSizeBytes = 0L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        player.play(rec)

        assertEquals("Audio file is corrupted or empty (0 bytes)", player.playbackState.value.errorMessage)
        assertFalse(player.playbackState.value.isPlaying)

        temp.delete()
    }

    @Test
    fun release_clearsSessionAndState() {
        player.stop()
        player.release()

        val state = player.playbackState.value
        assertNull(state.currentRecording)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
    }
}
