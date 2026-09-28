package com.example.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecordingQualityTest {

    @Test
    fun presets_haveExpectedFidelityValues() {
        // STANDARD (Speech & compact size)
        assertEquals(44100, RecordingQuality.STANDARD.sampleRate)
        assertEquals(96000, RecordingQuality.STANDARD.bitRate)
        assertEquals(1, RecordingQuality.STANDARD.channelCount)

        // HIGH (Balanced general audio)
        assertEquals(44100, RecordingQuality.HIGH.sampleRate)
        assertEquals(192000, RecordingQuality.HIGH.bitRate)
        assertEquals(1, RecordingQuality.HIGH.channelCount)

        // MAXIMUM (Studio fidelity)
        assertEquals(48000, RecordingQuality.MAXIMUM.sampleRate)
        assertEquals(256000, RecordingQuality.MAXIMUM.bitRate)
        assertEquals(1, RecordingQuality.MAXIMUM.channelCount)
    }

    @Test
    fun defaultPreset_isHighQuality() {
        assertEquals(RecordingQuality.HIGH, RecordingQuality.DEFAULT)
    }

    @Test
    fun toAudioConfig_producesCorrectConfiguration() {
        val standardConfig = RecordingQuality.STANDARD.toAudioConfig()
        assertEquals(RecordingFormat.M4A, standardConfig.format)
        assertEquals(44100, standardConfig.sampleRate)
        assertEquals(96000, standardConfig.bitRate)
        assertEquals(1, standardConfig.channelCount)

        val highConfig = RecordingQuality.HIGH.toAudioConfig()
        assertEquals(44100, highConfig.sampleRate)
        assertEquals(192000, highConfig.bitRate)

        val maxConfig = RecordingQuality.MAXIMUM.toAudioConfig()
        assertEquals(48000, maxConfig.sampleRate)
        assertEquals(256000, maxConfig.bitRate)
    }

    @Test
    fun fromId_resolvesExpectedPresetsAndFallbacks() {
        assertEquals(RecordingQuality.STANDARD, RecordingQuality.fromId("standard"))
        assertEquals(RecordingQuality.HIGH, RecordingQuality.fromId("high"))
        assertEquals(RecordingQuality.MAXIMUM, RecordingQuality.fromId("maximum"))

        // Case-insensitivity
        assertEquals(RecordingQuality.STANDARD, RecordingQuality.fromId("STANDARD"))
        assertEquals(RecordingQuality.MAXIMUM, RecordingQuality.fromId("Maximum"))

        // Unknown or null fallbacks safely to DEFAULT
        assertEquals(RecordingQuality.DEFAULT, RecordingQuality.fromId(null))
        assertEquals(RecordingQuality.DEFAULT, RecordingQuality.fromId("invalid_preset_id"))
        assertEquals(RecordingQuality.DEFAULT, RecordingQuality.fromId(""))
    }

    @Test
    fun descriptions_and_subtitles_arePopulated() {
        RecordingQuality.entries.forEach { quality ->
            assertNotNull(quality.title)
            assertNotNull(quality.subtitle)
            assertNotNull(quality.description)
            assert(quality.title.isNotBlank())
            assert(quality.subtitle.isNotBlank())
            assert(quality.description.isNotBlank())
        }
    }
}
