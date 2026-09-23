package com.example.core.engine

/**
 * Recording format abstraction for audio recording.
 * Phase 04 implements M4A (AAC-LC) using Android platform MediaRecorder.
 */
sealed class RecordingFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    object M4A : RecordingFormat(
        extension = "m4a",
        mimeType = "audio/mp4",
        displayName = "M4A (AAC)"
    )
}
