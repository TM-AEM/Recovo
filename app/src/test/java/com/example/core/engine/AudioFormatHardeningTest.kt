package com.example.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
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
class AudioFormatHardeningTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "audio_format_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun recordingQuality_presetsEnforceAuthoritativeInvariants() {
        // Standard: 44.1 kHz, 96 kbps, mono
        assertEquals(44100, RecordingQuality.STANDARD.sampleRate)
        assertEquals(96000, RecordingQuality.STANDARD.bitRate)
        assertEquals(1, RecordingQuality.STANDARD.channelCount)

        // High: 44.1 kHz, 192 kbps, mono
        assertEquals(44100, RecordingQuality.HIGH.sampleRate)
        assertEquals(192000, RecordingQuality.HIGH.bitRate)
        assertEquals(1, RecordingQuality.HIGH.channelCount)

        // Maximum: 48 kHz, 256 kbps (CRITICAL: strictly 256k, NOT 288k), mono
        assertEquals(48000, RecordingQuality.MAXIMUM.sampleRate)
        assertEquals(256000, RecordingQuality.MAXIMUM.bitRate)
        assertEquals(1, RecordingQuality.MAXIMUM.channelCount)

        // Format is M4A / audio/mp4 for all presets
        RecordingQuality.entries.forEach { quality ->
            val config = quality.toAudioConfig()
            assertEquals(RecordingFormat.M4A, config.format)
            assertEquals("m4a", config.format.extension)
            assertEquals("audio/mp4", config.format.mimeType)
            assertEquals(1, config.channelCount)
        }
    }

    @Test
    fun audioConfig_hierarchyAndDefaultValues() {
        val defaultConfig = AudioConfig()
        assertEquals(RecordingFormat.M4A, defaultConfig.format)
        assertEquals(44100, defaultConfig.sampleRate)
        assertEquals(128000, defaultConfig.bitRate)
        assertEquals(1, defaultConfig.channelCount)

        assertTrue(RecordingQuality.STANDARD.bitRate < RecordingQuality.HIGH.bitRate)
        assertTrue(RecordingQuality.HIGH.bitRate < RecordingQuality.MAXIMUM.bitRate)
    }

    @Test
    fun fileValidation_rejectsZeroByteAndSubMinimalFiles() {
        val zeroByteFile = File(testDir, "empty.m4a").apply { createNewFile() }
        assertTrue(zeroByteFile.exists())
        assertEquals(0L, zeroByteFile.length())

        // Subminimal file (< 32 bytes) cannot contain MPEG-4 container header
        val stubFile = File(testDir, "stub.m4a").apply {
            writeBytes(ByteArray(16) { 0x00 })
        }
        assertTrue(stubFile.exists())
        assertEquals(16L, stubFile.length())

        val validHeaderFile = File(testDir, "valid_header.m4a").apply {
            writeBytes(ByteArray(128) { 0x01 })
        }
        assertTrue(validHeaderFile.exists())
        assertEquals(128L, validHeaderFile.length())

        fun validate(file: File): Boolean {
            return if (!file.isFile || file.length() < 32L) {
                if (file.exists()) file.delete()
                false
            } else {
                true
            }
        }

        assertFalse(validate(zeroByteFile))
        assertFalse(zeroByteFile.exists())

        assertFalse(validate(stubFile))
        assertFalse(stubFile.exists())

        assertTrue(validate(validHeaderFile))
        assertTrue(validHeaderFile.exists())
    }

    @Test
    fun durationTracker_accuratelyTracksPausesAndResumptions() {
        var simulatedRealtime = 10000L
        val tracker = RecordingDurationTracker { simulatedRealtime }

        // Start at t=10000
        tracker.start()
        assertEquals(0L, tracker.elapsedMs())

        // Advance 2000ms while active
        simulatedRealtime += 2000L
        assertEquals(2000L, tracker.elapsedMs())

        // Pause at t=12000
        tracker.pause()
        assertEquals(2000L, tracker.elapsedMs())

        // Time elapses while paused for 5000ms
        simulatedRealtime += 5000L
        assertEquals(2000L, tracker.elapsedMs())

        // Resume at t=17000
        tracker.resume()
        assertEquals(2000L, tracker.elapsedMs())

        // Advance another 3000ms while active
        simulatedRealtime += 3000L
        assertEquals(5000L, tracker.elapsedMs())

        // Pause again
        tracker.pause()
        assertEquals(5000L, tracker.elapsedMs())

        // Advance another 10000ms while paused
        simulatedRealtime += 10000L
        assertEquals(5000L, tracker.elapsedMs())
    }

    @Test
    fun candidateFallbackProfiles_orderAndCompleteness() {
        val requestedConfig = RecordingQuality.MAXIMUM.toAudioConfig()
        val candidates = listOf(
            requestedConfig,
            if (requestedConfig.sampleRate != 48000) requestedConfig.copy(sampleRate = 48000) else requestedConfig.copy(sampleRate = 44100),
            AudioConfig(format = RecordingFormat.M4A, audioSource = requestedConfig.audioSource, sampleRate = 48000, bitRate = 128000, channelCount = 1),
            AudioConfig(format = RecordingFormat.M4A, audioSource = requestedConfig.audioSource, sampleRate = 44100, bitRate = 128000, channelCount = 1)
        ).distinct()

        // 1. Primary candidate must be the requested MAXIMUM configuration
        assertEquals(requestedConfig, candidates[0])
        assertEquals(48000, candidates[0].sampleRate)
        assertEquals(256000, candidates[0].bitRate)

        // 2. Secondary candidate alters sample rate to 44.1 kHz if 48 kHz is unsupported by OEM
        assertEquals(44100, candidates[1].sampleRate)
        assertEquals(256000, candidates[1].bitRate)

        // 3. Fallbacks provide universal baseline 128 kbps
        assertTrue(candidates.any { it.sampleRate == 48000 && it.bitRate == 128000 })
        assertTrue(candidates.any { it.sampleRate == 44100 && it.bitRate == 128000 })
    }

    @Test
    fun recordingSessionResult_mapsAudioFormatAccurately() {
        val dummyFile = File(testDir, "session_test.m4a").apply {
            writeBytes(ByteArray(256) { 0x02 })
        }

        val result = RecordingSessionResult(
            file = dummyFile,
            durationMs = 4500L,
            fileSizeBytes = dummyFile.length(),
            sampleRate = 48000,
            bitRate = 256000,
            channelCount = 1,
            mimeType = "audio/mp4",
            format = "M4A"
        )

        assertEquals("M4A", result.format)
        assertEquals("audio/mp4", result.mimeType)
        assertEquals(48000, result.sampleRate)
        assertEquals(256000, result.bitRate)
        assertEquals(1, result.channelCount)
        assertEquals(4500L, result.durationMs)
        assertEquals(256L, result.fileSizeBytes)
    }
}
