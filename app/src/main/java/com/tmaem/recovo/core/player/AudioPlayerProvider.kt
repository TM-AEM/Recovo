package com.tmaem.recovo.core.player

import android.content.Context

/**
 * Provides an application-scoped singleton of [AudioPlayer].
 * Ensures the foreground [PlaybackService], MediaSession, and UI share
 * the exact same state machine and audio engine.
 */
object AudioPlayerProvider {

    @Volatile
    private var instance: AudioPlayer? = null

    @Volatile
    private var positionSaver: PlaybackPositionSaver? = null

    fun get(context: Context): AudioPlayer {
        return instance ?: synchronized(this) {
            instance ?: AndroidAudioPlayer(context.applicationContext).also {
                instance = it
            }
        }
    }

    /**
     * Binds the persistence sink used to checkpoint/resume playback positions. Called once at
     * application start-up; also re-applies the sink to an already-created player.
     */
    fun setPositionSaver(saver: PlaybackPositionSaver?) {
        positionSaver = saver
        (instance as? AndroidAudioPlayer)?.setPositionSaver(saver)
    }

    /**
     * For testing purposes to inject test doubles.
     */
    fun setForTesting(player: AudioPlayer?) {
        instance = player
    }
}
