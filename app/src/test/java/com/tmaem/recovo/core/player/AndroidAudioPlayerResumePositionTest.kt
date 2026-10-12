package com.tmaem.recovo.core.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.service.RecordingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File

/**
 * Resume-position behaviour of [AndroidAudioPlayer]: restoration after preparation, event-driven
 * saves, and track-switch isolation. Uses the existing Robolectric [ShadowMediaPlayer] pattern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAudioPlayerResumePositionTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    /** (recordingId, positionMs) pairs captured by the synchronous save override. */
    private val saves = mutableListOf<Pair<Long, Long?>>()

    private var lastShadow: ShadowMediaPlayer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = AndroidAudioPlayer(context, testScope)
        player.positionSaveOverride = { id, pos -> saves.add(id to pos) }
        ShadowMediaPlayer.setCreateListener { _, shadow -> lastShadow = shadow }
        RecordingService.setRecordingActiveForTesting(false)
    }

    @After
    fun tearDown() {
        RecordingService.setRecordingActiveForTesting(false)
        player.stop()
        player.release()
        ShadowMediaPlayer.setCreateListener(null)
    }

    private fun audioFile(name: String, durationMs: Int): File {
        val file = File(context.cacheDir, "$name.m4a")
        if (file.exists()) file.delete()
        file.writeBytes(ByteArray(1024) { 0x55.toByte() })
        val ds = DataSource.toDataSource(file.absolutePath)
        ShadowMediaPlayer.addMediaInfo(ds, ShadowMediaPlayer.MediaInfo(durationMs, 0))
        return file
    }

    private fun recording(
        id: Long,
        file: File,
        resumePositionMs: Long? = null,
        durationMs: Long = 5000L
    ) = RecordingEntity(
        id = id,
        fileName = file.name,
        displayName = file.nameWithoutExtension,
        filePath = file.absolutePath,
        mimeType = "audio/mp4",
        format = "M4A",
        durationMs = durationMs,
        fileSizeBytes = file.length(),
        sampleRate = 44100,
        bitRate = 128000,
        channelCount = 1,
        resumePositionMs = resumePositionMs
    )

    @Test
    fun validStoredPosition_isRestoredAfterPreparation() {
        val file = audioFile("resume_valid", 5000)
        player.play(recording(1L, file, resumePositionMs = 3000L))
        ShadowLooper.idleMainLooper()

        val state = player.playbackState.value
        assertTrue(state.isPrepared)
        assertEquals(3000L, state.currentPositionMs)
        file.delete()
    }

    @Test
    fun nullStoredPosition_startsAtZero() {
        val file = audioFile("resume_null", 5000)
        player.play(recording(2L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()

        assertEquals(0L, player.playbackState.value.currentPositionMs)
        file.delete()
    }

    @Test
    fun negativeStoredPosition_isRejectedAndStartsAtZero() {
        val file = audioFile("resume_negative", 5000)
        player.play(recording(3L, file, resumePositionMs = -2000L))
        ShadowLooper.idleMainLooper()

        assertEquals(0L, player.playbackState.value.currentPositionMs)
        file.delete()
    }

    @Test
    fun positionAtOrBeyondDuration_doesNotResumeAtEof() {
        val file = audioFile("resume_eof", 5000)
        player.play(recording(4L, file, resumePositionMs = 5000L))
        ShadowLooper.idleMainLooper()
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        val file2 = audioFile("resume_beyond", 5000)
        player.play(recording(5L, file2, resumePositionMs = 9000L))
        ShadowLooper.idleMainLooper()
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        file.delete()
        file2.delete()
    }

    @Test
    fun unknownDuration_doesNotRestorePosition() {
        val file = audioFile("resume_unknown", 0)
        player.play(recording(6L, file, resumePositionMs = 3000L, durationMs = 0L))
        ShadowLooper.idleMainLooper()

        assertEquals(0L, player.playbackState.value.currentPositionMs)
        file.delete()
    }

    @Test
    fun seek_savesPositionImmediatelyForCurrentRecording() {
        val file = audioFile("resume_seek", 10000)
        player.play(recording(7L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        saves.clear()

        player.seekTo(4000L)

        assertEquals(listOf(7L to 4000L), saves)
        file.delete()
    }

    @Test
    fun pause_savesCurrentRecordingPosition() {
        val file = audioFile("resume_pause", 10000)
        player.play(recording(8L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.seekTo(2500L)
        saves.clear()

        player.pause()

        assertEquals(1, saves.size)
        assertEquals(8L, saves.first().first)
        assertEquals(2500L, saves.first().second)
        file.delete()
    }

    @Test
    fun stop_savesOutgoingPositionForItsOwnId() {
        val file = audioFile("resume_stop", 10000)
        player.play(recording(9L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.seekTo(6000L)
        saves.clear()

        player.stop()

        assertEquals(1, saves.size)
        assertEquals(9L, saves.first().first)
        assertEquals(6000L, saves.first().second)
        file.delete()
    }

    @Test
    fun switchingTracks_savesOutgoingAgainstItsOwnId_andIncomingDoesNotInherit() {
        val fileA = audioFile("switch_a", 10000)
        val fileB = audioFile("switch_b", 10000)

        player.play(recording(10L, fileA, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.seekTo(7000L)
        saves.clear()

        player.play(recording(11L, fileB, resumePositionMs = null))
        ShadowLooper.idleMainLooper()

        // The outgoing track is saved against its own id...
        assertTrue(saves.contains(10L to 7000L))
        // ...and nothing is ever recorded against the incoming track's id, so it cannot inherit.
        assertTrue(saves.none { it.first == 11L })
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        fileA.delete()
        fileB.delete()
    }

    @Test
    fun lateOutgoingSaves_neverTargetIncomingRecording() {
        val fileA = audioFile("late_a", 10000)
        val fileB = audioFile("late_b", 10000)

        player.play(recording(20L, fileA, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.seekTo(3000L)
        player.play(recording(21L, fileB, resumePositionMs = null))
        ShadowLooper.idleMainLooper()

        // Every captured save carries the id it was scheduled with; none targets the new track.
        assertTrue(saves.all { it.first == 20L })
        assertNull(saves.firstOrNull { it.first == 21L })

        fileA.delete()
        fileB.delete()
    }

    @Test
    fun naturalCompletion_repeatOff_atEndOfQueue_clearsSavedPosition() {
        val file = audioFile("complete_off", 5000)
        player.play(recording(30L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.seekTo(4000L)
        saves.clear()

        lastShadow!!.invokeCompletionListener()

        // Completed at the end of the queue: saved position is cleared, not left at end-of-file.
        assertEquals(listOf(30L to null), saves)
        assertEquals(5000L, player.playbackState.value.currentPositionMs)
        assertEquals(RepeatMode.OFF, player.playbackState.value.repeatMode)
        file.delete()
    }

    @Test
    fun naturalCompletion_repeatOne_replaysFromZeroAndClearsSavedPosition() {
        val file = audioFile("complete_one", 5000)
        player.play(recording(31L, file, resumePositionMs = null))
        ShadowLooper.idleMainLooper()
        player.setRepeatMode(RepeatMode.ONE)
        saves.clear()

        lastShadow!!.invokeCompletionListener()

        assertEquals(0L, player.playbackState.value.currentPositionMs)
        assertTrue(player.playbackState.value.isPlaying)
        assertEquals(listOf(31L to null), saves)
        file.delete()
    }

    @Test
    fun naturalCompletion_repeatOff_midQueue_advancesAndSavesOutgoing() {
        val fileA = audioFile("complete_queue_a", 5000)
        val fileB = audioFile("complete_queue_b", 5000)
        val recA = recording(40L, fileA, resumePositionMs = null)
        val recB = recording(41L, fileB, resumePositionMs = null)

        player.setPlaylist(listOf(recA, recB))
        player.play(recA)
        ShadowLooper.idleMainLooper()
        player.seekTo(4500L)
        saves.clear()

        lastShadow!!.invokeCompletionListener()
        ShadowLooper.idleMainLooper()

        // Outgoing saved against its own id; the incoming track starts at zero (no inheritance).
        assertTrue(saves.contains(40L to 4500L))
        assertTrue(saves.none { it.first == 41L })
        assertEquals(41L, player.playbackState.value.currentRecording?.id)
        assertEquals(0L, player.playbackState.value.currentPositionMs)

        fileA.delete()
        fileB.delete()
    }

    // --- Periodic checkpoint loop (Task 34-6D) ---
    //
    // The checkpoint coroutine runs on the injected TestScope, so its delay() is driven by the
    // virtual test scheduler. The shadow's playback position is frozen at the seek target
    // (seekTo -> doStop -> no real-time advancement), and the main looper is never idled across
    // an interval boundary, so no natural completion fires while we advance virtual time.

    @Test
    fun periodicCheckpoint_noneBeforeInterval_onePerInterval_withActiveId() {
        val file = audioFile("chkpt_period", 12000)
        player.play(recording(100L, file, durationMs = 12000L))
        ShadowLooper.idleMainLooper()
        player.seekTo(7000L)
        // Wait for the pending seek-complete event before trusting the shadow's position.
        ShadowLooper.idleMainLooper()
        saves.clear()

        // 1. Nothing before the configured interval, despite ~19 ticker ticks (100 ms each).
        testScope.testScheduler.advanceTimeBy(1990L)
        testScope.testScheduler.runCurrent()
        assertTrue("no checkpoint before 2000 ms interval", saves.isEmpty())

        // 2. First checkpoint exactly at the 2000 ms boundary.
        testScope.testScheduler.advanceTimeBy(10L)
        testScope.testScheduler.runCurrent()
        assertEquals(listOf(100L to 7000L), saves)

        // 3. Repeated checkpoints follow the 2000 ms interval, not the 100 ms ticker: by t=4000
        //    the ticker has sampled ~40 times yet only two checkpoints exist, each carrying the
        //    currently active recording's stable id and position.
        testScope.testScheduler.advanceTimeBy(1999L)
        testScope.testScheduler.runCurrent()
        assertEquals(1, saves.size)
        testScope.testScheduler.advanceTimeBy(1L)
        testScope.testScheduler.runCurrent()
        assertEquals(2, saves.size)
        testScope.testScheduler.advanceTimeBy(6000L)
        testScope.testScheduler.runCurrent()
        assertEquals(5, saves.size)
        assertTrue(saves.all { it == (100L to 7000L) })

        file.delete()
    }

    @Test
    fun periodicCheckpoint_cancelledByStopAndRelease_noFurtherWrites() {
        val file = audioFile("chkpt_cancel", 12000)
        player.play(recording(400L, file, durationMs = 12000L))
        ShadowLooper.idleMainLooper()
        player.seekTo(6000L)
        ShadowLooper.idleMainLooper()
        saves.clear()

        testScope.testScheduler.advanceTimeBy(2000L)
        testScope.testScheduler.runCurrent()
        assertEquals(1, saves.size)

        // A later seek keeps the loop alive with the id stable.
        player.seekTo(3000L)
        ShadowLooper.idleMainLooper()
        val afterSecondSeek = saves.size
        testScope.testScheduler.advanceTimeBy(2000L)
        testScope.testScheduler.runCurrent()
        assertTrue("loop still checkpoints after a seek", saves.size > afterSecondSeek)

        // stop() saves the outgoing position and must cancel the periodic loop.
        player.stop()
        val frozen = saves.size
        testScope.testScheduler.advanceTimeBy(20_000L)
        testScope.testScheduler.runCurrent()
        assertEquals("no checkpoint writes after stop", frozen, saves.size)

        // release() after stop is benign: no recording is loaded, and the scope is cancelled.
        player.release()
        testScope.testScheduler.advanceTimeBy(5_000L)
        testScope.testScheduler.runCurrent()
        assertEquals("no writes after release", frozen, saves.size)
        assertTrue("all saves target the single active recording", saves.all { it.first == 400L })

        file.delete()
    }

    @Test
    fun switchingTracks_preventsStaleCheckpointsFromOverwritingIncoming() {
        val fileA = audioFile("chkpt_switch_a", 12000)
        val fileB = audioFile("chkpt_switch_b", 12000)
        val recA = recording(200L, fileA, durationMs = 12000L)
        val recB = recording(300L, fileB, durationMs = 12000L)

        player.play(recA)
        ShadowLooper.idleMainLooper()
        player.seekTo(9000L)
        ShadowLooper.idleMainLooper()
        saves.clear()
        testScope.testScheduler.advanceTimeBy(2000L)
        testScope.testScheduler.runCurrent()
        assertEquals(listOf(200L to 9000L), saves)

        // Switching cancels the old loop and records the outgoing track against its own id.
        player.play(recB)
        ShadowLooper.idleMainLooper()
        saves.clear()

        // The fresh loop is bound to the incoming recording: its saves target recB and are
        // normalized against recB's reset position, never the stale 9000 ms.
        testScope.testScheduler.advanceTimeBy(2000L)
        testScope.testScheduler.runCurrent()
        assertTrue("checkpoint loop must still be live after switch", saves.isNotEmpty())
        assertTrue("every post-switch save targets the incoming id", saves.all { it.first == 300L })
        assertTrue("incoming track must not inherit the stale 9000 ms position", saves.all { it.second == null })

        fileA.delete()
        fileB.delete()
    }
}
