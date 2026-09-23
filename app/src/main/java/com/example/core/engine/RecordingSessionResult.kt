package com.example.core.engine

import java.io.File

/**
 * Result data class when an audio recording session successfully completes.
 */
data class RecordingSessionResult(
    val file: File,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val sampleRate: Int,
    val bitRate: Int,
    val channelCount: Int,
    val mimeType: String,
    val format: String
)
