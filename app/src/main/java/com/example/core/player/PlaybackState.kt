package com.example.core.player

import com.example.core.database.model.RecordingEntity

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

data class PlaybackState(
    val currentRecording: RecordingEntity? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPrepared: Boolean = false,
    val errorMessage: String? = null,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val sleepTimerRemainingMs: Long? = null
)

