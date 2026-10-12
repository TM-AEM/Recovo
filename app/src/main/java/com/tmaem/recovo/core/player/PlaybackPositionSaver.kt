package com.tmaem.recovo.core.player

/**
 * Persistence boundary for playback resume positions.
 *
 * The player owns playback independently of the Library screen, so it must not depend on the
 * feature-layer repository type directly. Implementations are expected to be safe to call from
 * any coroutine: work must be dispatched off the main thread and failures must not crash playback.
 */
interface PlaybackPositionSaver {
    /**
     * Saves [positionMs] (milliseconds) for the recording identified by [recordingId].
     * Passing null clears the saved position.
     */
    suspend fun save(recordingId: Long, positionMs: Long?)
}
