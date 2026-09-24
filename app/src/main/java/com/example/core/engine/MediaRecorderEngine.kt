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

/** Monotonic duration tracker that can be tested without a physical microphone. */
internal class RecordingDurationTracker(private val elapsedRealtime: () -> Long) {
    private var accumulatedMs = 0L
    private var activeSegmentStartMs: Long? = null

    fun start() {
        accumulatedMs = 0L
        activeSegmentStartMs = elapsedRealtime()
    }

    fun pause() {
        val start = activeSegmentStartMs ?: return
        accumulatedMs += (elapsedRealtime() - start).coerceAtLeast(0L)
        activeSegmentStartMs = null
    }

    fun resume() {
        if (activeSegmentStartMs == null) activeSegmentStartMs = elapsedRealtime()
    }

    fun elapsedMs(): Long {
        val active = activeSegmentStartMs?.let {
            (elapsedRealtime() - it).coerceAtLeast(0L)
        } ?: 0L
        return accumulatedMs + active
    }
}


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
    private val durationTracker = RecordingDurationTracker(SystemClock::elapsedRealtime)

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

            try {
                val configuredRecorder = createAndConfigureRecorder(targetFile, config)
                recorder = configuredRecorder
                configuredRecorder.prepare()
                configuredRecorder.start()
                durationTracker.start()

                _state.value = RecordingState.Recording(file = targetFile, elapsedMs = 0L, amplitude = 0)
                Result.success(Unit)
            } catch (e: Exception) {
                releaseRecorderInternal()
                targetFile.delete()
                currentFile = null
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
                currentConfig = config.copy(sampleRate = 48000, bitRate = 128000, channelCount = 1)
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
                val activeRecorder = recorder ?: error("MediaRecorder is not initialized")
                activeRecorder.pause()
                durationTracker.pause()
                _state.value = RecordingState.Paused(
                    file = currentState.file,
                    elapsedMs = durationTracker.elapsedMs(),
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
                val activeRecorder = recorder ?: error("MediaRecorder is not initialized")
                activeRecorder.resume()
                durationTracker.resume()
                _state.value = RecordingState.Recording(
                    file = currentState.file,
                    elapsedMs = durationTracker.elapsedMs(),
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

            val totalDurationMs = durationTracker.elapsedMs()

            try {
                val activeRecorder = recorder ?: error("MediaRecorder is not initialized")
                activeRecorder.stop()
                releaseRecorderInternal()

                validateRecordingFile(file)
                val metadata = readMetadata(file)
                val result = RecordingSessionResult(
                    file = file,
                    durationMs = totalDurationMs,
                    fileSizeBytes = file.length(),
                    sampleRate = metadata.sampleRate,
                    bitRate = metadata.bitRate,
                    channelCount = metadata.channelCount,
                    mimeType = metadata.mimeType,
                    format = currentConfig.format.extension.uppercase()
                )

                _state.value = RecordingState.Idle
                Result.success(result)
            } catch (e: Exception) {
                releaseRecorderInternal()
                if (!file.isFile || file.length() <= 0L) file.delete()
                currentFile = null
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
                durationTracker.start()

                _state.value = RecordingState.Idle
                Result.success(Unit)
            } catch (e: Exception) {
                releaseRecorderInternal()
                _state.value = RecordingState.Idle
                Result.failure(e)
            }
        }
    }

    private data class ValidatedMetadata(
        val sampleRate: Int,
        val bitRate: Int,
        val channelCount: Int,
        val mimeType: String
    )

    private fun validateRecordingFile(file: File) {
        if (!file.isFile || file.length() <= 0L) {
            throw IOException("Recording file is missing or empty")
        }
    }

    private fun readMetadata(file: File): ValidatedMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun value(key: Int, fallback: Int): Int =
                retriever.extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 } ?: fallback
            ValidatedMetadata(
                sampleRate = value(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE, currentConfig.sampleRate),
                bitRate = value(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE, currentConfig.bitRate),
                channelCount = value(android.media.MediaMetadataRetriever.METADATA_KEY_CHANNEL_COUNT, currentConfig.channelCount),
                mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.takeIf { it.isNotBlank() } ?: currentConfig.format.mimeType
            )
        } catch (e: Exception) {
            throw IOException("Unable to validate recording metadata", e)
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun releaseRecorderInternal() {
        val activeRecorder = recorder ?: return
        try { activeRecorder.reset() } catch (_: Exception) { }
        try { activeRecorder.release() } catch (_: Exception) { }
        recorder = null
    }

    fun updateCurrentDuration() {
        synchronized(this) {
            val s = _state.value
            if (s is RecordingState.Recording) {
                _state.value = s.copy(elapsedMs = durationTracker.elapsedMs(), amplitude = getAmplitude())
            }
        }
    }
}
