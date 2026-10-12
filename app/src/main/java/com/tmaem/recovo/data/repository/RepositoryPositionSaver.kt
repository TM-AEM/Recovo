package com.tmaem.recovo.data.repository

import com.tmaem.recovo.core.player.PlaybackPositionSaver

/**
 * Bridges the playback layer's [PlaybackPositionSaver] contract to the existing recording
 * repository, so resume positions are stored behind the same abstraction as every other
 * recording mutation (and off the main thread via the repository's IO dispatcher).
 */
class RepositoryPositionSaver(
    private val repository: RecordingRepository
) : PlaybackPositionSaver {

    override suspend fun save(recordingId: Long, positionMs: Long?) {
        repository.saveResumePosition(recordingId, positionMs)
    }
}
