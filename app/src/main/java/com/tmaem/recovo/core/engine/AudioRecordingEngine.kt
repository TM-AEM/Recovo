package com.tmaem.recovo.core.engine

import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the Audio Recording Engine contract.
 */
interface AudioRecordingEngine {
    val state: StateFlow<RecordingState>
    fun getAmplitude(): Int

    suspend fun start(targetFile: File, config: AudioConfig = AudioConfig()): Result<Unit>
    suspend fun pause(isInterrupted: Boolean = false): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<RecordingSessionResult>
    suspend fun cancel(): Result<Unit>
}
