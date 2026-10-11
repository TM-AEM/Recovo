package com.tmaem.recovo.core.player

import com.tmaem.recovo.core.database.model.RecordingEntity
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val playbackState: StateFlow<PlaybackState>
    fun play(recording: RecordingEntity)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun seekRelative(offsetMs: Long)
    fun playNext()
    fun playPrevious()
    fun stop()
    fun release()
    fun clearError()
    fun setPlaylist(recordings: List<RecordingEntity>)
    fun setSpeed(speed: Float)
    fun startTemporaryFastForward()
    fun stopTemporaryFastForward()
    fun setRepeatMode(mode: RepeatMode)
    fun toggleRepeatMode()
    fun setShuffleEnabled(enabled: Boolean)
    fun toggleShuffle()
    fun setSleepTimer(durationMinutes: Int?)
    fun cancelSleepTimer()
    fun updateCurrentRecordingMetadata(recording: RecordingEntity)
}
