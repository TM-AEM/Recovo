package com.tmaem.recovo.core.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.player.AndroidAudioPlayer
import com.tmaem.recovo.core.player.AudioPlayerProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File

/**
 * Verifies that [PlaybackService] destruction requests a persistence of the current playback
 * position through the process-scoped [AndroidAudioPlayer] (Task 34-6D).
 *
 * The player is injected via [AudioPlayerProvider.setForTesting] and its
 * `positionSaveOverride` seam records `(recordingId, positionMs)` synchronously, so the assertions
 * target the real production call path (`onDestroy` -> `persistCurrentPositionBlocking`) without
 * any hardware or OS lifecycle claims. The Android main looper is only idled to complete
 * preparation and a seek; it is never advanced across a completion boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceResumeLifecycleTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    /** (recordingId, positionMs) pairs captured by the synchronous save override. */
    private val saves = mutableListOf<Pair<Long, Long?>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = AndroidAudioPlayer(context, testScope)
        player.positionSaveOverride = { id, pos -> saves.add(id to pos) }
        AudioPlayerProvider.setForTesting(player)
        RecordingService.setRecordingActiveForTesting(false)
    }

    @After
    fun tearDown() {
        AudioPlayerProvider.setForTesting(null)
        RecordingService.setRecordingActiveForTesting(false)
        player.stop()
        player.release()
    }

    private fun audioFile(name: String, durationMs: Int): File {
        val file = File(context.cacheDir, "$name.m4a")
        if (file.exists()) file.delete()
        file.writeBytes(ByteArray(1024) { 0x55.toByte() })
        val ds = DataSource.toDataSource(file.absolutePath)
        ShadowMediaPlayer.addMediaInfo(ds, ShadowMediaPlayer.MediaInfo(durationMs, 0))
        return file
    }

    private fun recording(id: Long, file: File, durationMs: Long = 12000L) = RecordingEntity(
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
        channelCount = 1
    )

    @Test
    fun serviceDestruction_persistsCurrentPositionWithCorrectId() {
        val file = audioFile("svc_destroy", 12000)
        player.play(recording(77L, file, durationMs = 12000L))
        ShadowLooper.idleMainLooper()
        player.seekTo(7777L)
        // Wait for the seek-complete event so the tracked position is authoritative.
        ShadowLooper.idleMainLooper()
        saves.clear()

        val controller = Robolectric.buildService(PlaybackService::class.java)
        assertNotNull(controller.create().get())
        controller.destroy()

        assertEquals("destroy must persist the active recording's position", listOf(77L to 7777L), saves)
        file.delete()
    }

    @Test
    fun serviceDestruction_withoutActiveRecording_isSafe() {
        saves.clear()

        val controller = Robolectric.buildService(PlaybackService::class.java)
        assertNotNull(controller.create().get())
        controller.destroy()

        assertTrue("no recording loaded means nothing is persisted", saves.isEmpty())
    }

    @Test
    fun serviceDestruction_whenPlayerUnavailable_isSafe() {
        // No injected player: the provider lazily creates its own instance. Destroy must not throw,
        // and nothing may be persisted for a player that has no active recording.
        AudioPlayerProvider.setForTesting(null)

        val controller = Robolectric.buildService(PlaybackService::class.java)
        assertNotNull(controller.create().get())
        controller.destroy()

        assertTrue("no stale write without an active recording", saves.isEmpty())
    }

    @Test
    fun repeatedServiceDestruction_doesNotDuplicatePlayerOrWriteStale() {
        val file = audioFile("svc_repeat", 12000)
        player.play(recording(88L, file, durationMs = 12000L))
        ShadowLooper.idleMainLooper()
        player.seekTo(5000L)
        ShadowLooper.idleMainLooper()
        saves.clear()

        val first = Robolectric.buildService(PlaybackService::class.java)
        assertNotNull(first.create().get())
        first.destroy()

        val second = Robolectric.buildService(PlaybackService::class.java)
        assertNotNull(second.create().get())
        second.destroy()

        // The provider instance is reused, never recreated, and every write targets the stable id.
        assertSame(player, AudioPlayerProvider.get(context))
        assertEquals(listOf(88L to 5000L, 88L to 5000L), saves)
        assertTrue(saves.all { it.first == 88L })
        file.delete()
    }
}
