package com.example.core.engine

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Robust MediaRecorder implementation of [AudioRecordingEngine].
 * Compatible with Android 11+ (API 30+) through Android 16+ (API 36+), including Samsung devices.
 * Uses native AAC-LC in MP4 container (M4A) with safe fallbacks and explicit resource release.
 */
class MediaRecorderEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioRecordingEngine {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var currentConfig: AudioConfig = AudioConfig()

    // Precise time tracking independent of UI frames
    private var recordingStartTimeUptime: Long = 0L
    private var accumulatedRecordedDurationMs: Long = 0L
    private var pauseStartTimeUptime: Long = 0L

    override fun getAmplitude(): Int {
        return try {
            val rec = recorder
            val s = _state.value
            if (rec != null && s is RecordingState.Recording) {
                rec.maxAmplitude
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun start(targetFile: File, config: AudioConfig): Result<Unit> = withContext(ioDispatcher) {
        synchronized(this@MediaRecorderEngine) {
            val currentState = _state.value
            if (currentState !is RecordingState.Idle && currentState !is RecordingState.Saved && currentState !is RecordingState.Error) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot start recording while in state: $currentState")
                )
            }

            _state.value = RecordingState.Preparing
            currentFile = targetFile
            currentConfig = config
            accumulatedRecordedDurationMs = 0L

            try {
                val newRecorder = createAndConfigureRecorder(targetFile, config)
                newRecorder.prepare()
                newRecorder.start()

                recorder = newRecorder
                recordingStartTimeUptime = SystemClock.uptimeMillis()
                _state.value = RecordingState.Recording(file = targetFile, elapsedMs = 0L, amplitude = 0)
                Result.success(Unit)
            } catch (e: Exception) {
                releaseRecorderInternal()
                _state.value = RecordingState.Error("Failed to start recording: ${e.localizedMessage ?: e.javaClass.simpleName}", e)
                Result.failure(e)
            }
        }
    }

    private fun createAndConfigureRecorder(targetFile: File, config: AudioConfig): MediaRecorder {
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            newRecorder.setAudioSource(config.audioSource)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(config.bitRate)
            newRecorder.setAudioSamplingRate(config.sampleRate)
            newRecorder.setAudioChannels(config.channelCount)
            newRecorder.setOutputFile(targetFile.absolutePath)
            return newRecorder
        } catch (e: Exception) {
            // Fallback for devices with strict samplerate constraints (e.g. 48000Hz fallback)
            try {
                newRecorder.reset()
                newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                newRecorder.setAudioSamplingRate(48000)
                newRecorder.setAudioEncodingBitRate(128000)
                newRecorder.setAudioChannels(1)
                newRecorder.setOutputFile(targetFile.absolutePath)
                return newRecorder
            } catch (fallbackEx: Exception) {
                newRecorder.release()
                throw IOException("Unable to initialize MediaRecorder: ${e.message}", e)
            }
        }
    }

    override suspend fun pause(): Result<Unit> = withContext(ioDispatcher) {
        synchronized(this@MediaRecorderEngine) {
            val currentState = _state.value
            if (currentState !is RecordingState.Recording) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot pause when not actively recording. State: $currentState")
                )
            }

            try {
                recorder?.pause()
                val now = SystemClock.uptimeMillis()
                accumulatedRecordedDurationMs += (now - recordingStartTimeUptime)
                pauseStartTimeUptime = now

                _state.value = RecordingState.Paused(
                    file = currentState.file,
                    elapsedMs = accumulatedRecordedDurationMs,
                    amplitude = 0
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun resume(): Result<Unit> = withContext(ioDispatcher) {
        synchronized(this@MediaRecorderEngine) {
            val currentState = _state.value
            if (currentState !is RecordingState.Paused) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot resume when not in Paused state. State: $currentState")
                )
            }

            try {
                recorder?.resume()
                recordingStartTimeUptime = SystemClock.uptimeMillis()
                _state.value = RecordingState.Recording(
                    file = currentState.file,
                    elapsedMs = accumulatedRecordedDurationMs,
                    amplitude = 0
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun stop(): Result<RecordingSessionResult> = withContext(ioDispatcher) {
        synchronized(this@MediaRecorderEngine) {
            val currentState = _state.value
            if (currentState !is RecordingState.Recording && currentState !is RecordingState.Paused) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot stop when not recording or paused. State: $currentState")
                )
            }

            val file = currentFile ?: return@withContext Result.failure(
                IllegalStateException("No current recording file exists")
            )

            _state.value = RecordingState.Stopping

            var totalDurationMs = accumulatedRecordedDurationMs
            if (currentState is RecordingState.Recording) {
                val now = SystemClock.uptimeMillis()
                totalDurationMs += (now - recordingStartTimeUptime)
            }

            try {
                try {
                    recorder?.stop()
                } catch (stopEx: RuntimeException) {
                    // MediaRecorder may throw RuntimeException if stop() is called immediately after start()
                    // or if no valid audio frames were captured.
                }
                releaseRecorderInternal()

                val fileSize = if (file.exists()) file.length() else 0L

                val result = RecordingSessionResult(
                    file = file,
                    durationMs = totalDurationMs,
                    fileSizeBytes = fileSize,
                    sampleRate = currentConfig.sampleRate,
                    bitRate = currentConfig.bitRate,
                    channelCount = currentConfig.channelCount,
                    mimeType = currentConfig.format.mimeType,
                    format = currentConfig.format.extension.uppercase()
                )

                _state.value = RecordingState.Idle
                Result.success(result)
            } catch (e: Exception) {
                releaseRecorderInternal()
                _state.value = RecordingState.Error("Error stopping recording: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun cancel(): Result<Unit> = withContext(ioDispatcher) {
        synchronized(this@MediaRecorderEngine) {
            try {
                try {
                    recorder?.stop()
                } catch (ignored: Exception) {
                }
                releaseRecorderInternal()

                currentFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
                currentFile = null
                accumulatedRecordedDurationMs = 0L

                _state.value = RecordingState.Idle
                Result.success(Unit)
            } catch (e: Exception) {
                releaseRecorderInternal()
                _state.value = RecordingState.Idle
                Result.failure(e)
            }
        }
    }

    private fun releaseRecorderInternal() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (ignored: Exception) {
        } finally {
            recorder = null
        }
    }

    fun updateCurrentDuration() {
        synchronized(this) {
            val s = _state.value
            if (s is RecordingState.Recording) {
                val now = SystemClock.uptimeMillis()
                val currentElapsed = accumulatedRecordedDurationMs + (now - recordingStartTimeUptime)
                val amp = getAmplitude()
                _state.value = s.copy(elapsedMs = currentElapsed, amplitude = amp)
            }
        }
    }
}
