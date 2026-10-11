package com.tmaem.recovo.core.engine

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

            try {
                val (configuredRecorder, effectiveConfig) = createAndPrepareRecorder(targetFile, config)
                recorder = configuredRecorder
                currentConfig = effectiveConfig
                configuredRecorder.start()
                durationTracker.start()

                _state.value = RecordingState.Recording(file = targetFile, elapsedMs = 0L, amplitude = 0)
                Result.success(Unit)
            } catch (e: Exception) {
                releaseRecorderInternal()
                if (targetFile.exists()) targetFile.delete()
                currentFile = null
                _state.value = RecordingState.Error("Failed to start recording: ${e.localizedMessage ?: e.javaClass.simpleName}", e)
                Result.failure(e)
            }
        }
    }

    private fun createRecorderInstance(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun configureRecorder(
        recorder: MediaRecorder,
        targetFile: File,
        config: AudioConfig
    ) {
        recorder.setOnErrorListener { _, what, extra ->
            synchronized(this@MediaRecorderEngine) {
                _state.value = RecordingState.Error("MediaRecorder error: what=$what, extra=$extra")
            }
        }
        recorder.setOnInfoListener { _, what, extra ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
            ) {
                // Max limit reached
            }
        }

        recorder.setAudioSource(config.audioSource)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(config.bitRate)
        recorder.setAudioSamplingRate(config.sampleRate)
        recorder.setAudioChannels(config.channelCount)
        recorder.setOutputFile(targetFile.absolutePath)
    }

    private fun createAndPrepareRecorder(
        targetFile: File,
        requestedConfig: AudioConfig
    ): Pair<MediaRecorder, AudioConfig> {
        val candidates = listOf(
            requestedConfig,
            if (requestedConfig.sampleRate != 48000) requestedConfig.copy(sampleRate = 48000) else requestedConfig.copy(sampleRate = 44100),
            AudioConfig(format = RecordingFormat.M4A, audioSource = requestedConfig.audioSource, sampleRate = 48000, bitRate = 128000, channelCount = 1),
            AudioConfig(format = RecordingFormat.M4A, audioSource = requestedConfig.audioSource, sampleRate = 44100, bitRate = 128000, channelCount = 1)
        ).distinct()

        var lastException: Exception? = null
        for (candidate in candidates) {
            val rec = createRecorderInstance()
            try {
                configureRecorder(rec, targetFile, candidate)
                rec.prepare()
                return Pair(rec, candidate)
            } catch (e: Exception) {
                lastException = e
                try {
                    rec.setOnErrorListener(null)
                    rec.setOnInfoListener(null)
                    rec.reset()
                } catch (_: Exception) { }
                try {
                    rec.release()
                } catch (_: Exception) { }
            }
        }

        throw IOException(
            "Unable to prepare MediaRecorder with requested config ($requestedConfig) or fallback profiles: ${lastException?.message}",
            lastException
        )
    }

    override suspend fun pause(isInterrupted: Boolean): Result<Unit> = withContext(ioDispatcher) {
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
                    amplitude = 0,
                    isInterrupted = isInterrupted
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
                try {
                    activeRecorder.stop()
                } catch (stopEx: Exception) {
                    releaseRecorderInternal()
                    if (file.exists()) file.delete()
                    currentFile = null
                    _state.value = RecordingState.Error("Failed to finalize recording: ${stopEx.message}", stopEx)
                    return@withContext Result.failure(stopEx)
                }
                releaseRecorderInternal()

                validateRecordingFile(file)
                val metadata = readMetadata(file)
                val finalDurationMs = if (totalDurationMs > 0L) {
                    totalDurationMs
                } else {
                    metadata.durationMs.takeIf { it > 0L } ?: 0L
                }

                val result = RecordingSessionResult(
                    file = file,
                    durationMs = finalDurationMs,
                    fileSizeBytes = file.length(),
                    sampleRate = metadata.sampleRate,
                    bitRate = metadata.bitRate,
                    channelCount = metadata.channelCount,
                    mimeType = metadata.mimeType,
                    format = currentConfig.format.extension.uppercase()
                )

                currentFile = null
                _state.value = RecordingState.Idle
                Result.success(result)
            } catch (e: Exception) {
                releaseRecorderInternal()
                if (file.exists() && (!file.isFile || file.length() < 32L)) file.delete()
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
        val mimeType: String,
        val durationMs: Long
    )

    private fun validateRecordingFile(file: File) {
        if (!file.isFile || file.length() < 32L) {
            if (file.exists()) file.delete()
            throw IOException("Recording file is missing, empty, or smaller than minimal audio container header")
        }
    }

    private fun readMetadata(file: File): ValidatedMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun value(key: Int, fallback: Int): Int =
                retriever.extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 } ?: fallback
            val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
            ValidatedMetadata(
                sampleRate = value(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE, currentConfig.sampleRate),
                bitRate = value(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE, currentConfig.bitRate),
                channelCount = currentConfig.channelCount,
                mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.takeIf { it.isNotBlank() } ?: currentConfig.format.mimeType,
                durationMs = dur
            )
        } catch (_: Exception) {
            ValidatedMetadata(
                sampleRate = currentConfig.sampleRate,
                bitRate = currentConfig.bitRate,
                channelCount = currentConfig.channelCount,
                mimeType = currentConfig.format.mimeType,
                durationMs = 0L
            )
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun releaseRecorderInternal() {
        val activeRecorder = recorder ?: return
        try {
            activeRecorder.setOnErrorListener(null)
            activeRecorder.setOnInfoListener(null)
            activeRecorder.reset()
        } catch (_: Exception) { }
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
