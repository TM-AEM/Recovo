package com.example.core.engine

import java.io.File

/**
 * Clean Single-State Machine representing the exact recording lifecycle.
 * Prevents illegal states (e.g. isRecording && isPaused).
 */
sealed class RecordingState {
    object Idle : RecordingState()
    object Preparing : RecordingState()
    data class Recording(
        val file: File,
        val elapsedMs: Long = 0L,
        val amplitude: Int = 0
    ) : RecordingState()
    data class Paused(
        val file: File,
        val elapsedMs: Long,
        val amplitude: Int = 0
    ) : RecordingState()
    object Stopping : RecordingState()
    data class Saved(val file: File, val recordingId: Long) : RecordingState()
    data class Error(val message: String, val cause: Throwable? = null) : RecordingState()
}
