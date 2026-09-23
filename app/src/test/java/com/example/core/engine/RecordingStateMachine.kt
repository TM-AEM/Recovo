package com.example.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Clean State Machine coordinator for unit testing state transitions and business rules
 * completely decoupled from the physical Android microphone hardware.
 */
class RecordingStateMachine(
    private val onStart: suspend (File, AudioConfig) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    private val onPause: suspend () -> Result<Unit> = { Result.success(Unit) },
    private val onResume: suspend () -> Result<Unit> = { Result.success(Unit) },
    private val onStop: suspend () -> Result<RecordingSessionResult> = {
        Result.success(
            RecordingSessionResult(
                file = File("/mock/test.m4a"),
                durationMs = 5000L,
                fileSizeBytes = 12000L,
                sampleRate = 44100,
                bitRate = 128000,
                channelCount = 1,
                mimeType = "audio/mp4",
                format = "M4A"
            )
        )
    },
    private val onCancel: suspend () -> Result<Unit> = { Result.success(Unit) }
) {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    suspend fun start(targetFile: File, config: AudioConfig = AudioConfig()): Result<Unit> {
        val current = _state.value
        if (current !is RecordingState.Idle && current !is RecordingState.Saved && current !is RecordingState.Error) {
            return Result.failure(IllegalStateException("Invalid transition to START from state: $current"))
        }

        _state.value = RecordingState.Preparing
        val result = onStart(targetFile, config)
        if (result.isSuccess) {
            _state.value = RecordingState.Recording(file = targetFile, elapsedMs = 0L, amplitude = 0)
        } else {
            _state.value = RecordingState.Error(result.exceptionOrNull()?.message ?: "Start failed")
        }
        return result
    }

    suspend fun pause(): Result<Unit> {
        val current = _state.value
        if (current !is RecordingState.Recording) {
            return Result.failure(IllegalStateException("Invalid transition to PAUSE from state: $current"))
        }

        val result = onPause()
        if (result.isSuccess) {
            _state.value = RecordingState.Paused(
                file = current.file,
                elapsedMs = current.elapsedMs,
                amplitude = 0
            )
        }
        return result
    }

    suspend fun resume(): Result<Unit> {
        val current = _state.value
        if (current !is RecordingState.Paused) {
            return Result.failure(IllegalStateException("Invalid transition to RESUME from state: $current"))
        }

        val result = onResume()
        if (result.isSuccess) {
            _state.value = RecordingState.Recording(
                file = current.file,
                elapsedMs = current.elapsedMs,
                amplitude = 0
            )
        }
        return result
    }

    suspend fun stop(): Result<RecordingSessionResult> {
        val current = _state.value
        if (current !is RecordingState.Recording && current !is RecordingState.Paused) {
            return Result.failure(IllegalStateException("Invalid transition to STOP from state: $current"))
        }

        _state.value = RecordingState.Stopping
        val result = onStop()
        if (result.isSuccess) {
            val session = result.getOrThrow()
            _state.value = RecordingState.Saved(session.file, recordingId = 1L)
        } else {
            _state.value = RecordingState.Error(result.exceptionOrNull()?.message ?: "Stop failed")
        }
        return result
    }

    suspend fun cancel(): Result<Unit> {
        val result = onCancel()
        _state.value = RecordingState.Idle
        return result
    }

    fun resetToIdle() {
        _state.value = RecordingState.Idle
    }
}
