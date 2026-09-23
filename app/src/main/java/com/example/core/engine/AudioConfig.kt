package com.example.core.engine

import android.media.MediaRecorder

/**
 * Audio recording configuration.
 * Default values are broad-compatibility standards safe across Android 11+ and Samsung devices:
 * - Audio Source: MediaRecorder.AudioSource.MIC
 * - Sample rate: 44100 Hz (CD quality, universally supported) with 48000 Hz fallback
 * - Bitrate: 128 kbps (clear speech & audio fidelity)
 * - Channel count: 1 (Mono - universally reliable across all device microphones)
 */
data class AudioConfig(
    val format: RecordingFormat = RecordingFormat.M4A,
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val sampleRate: Int = 44100,
    val bitRate: Int = 128000,
    val channelCount: Int = 1
)
