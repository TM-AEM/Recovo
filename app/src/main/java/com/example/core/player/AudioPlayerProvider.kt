package com.example.core.player

import android.content.Context

/**
 * Provides an application-scoped singleton of [AudioPlayer].
 * Ensures the foreground [PlaybackService], MediaSession, and UI share
 * the exact same state machine and audio engine.
 */
object AudioPlayerProvider {

    @Volatile
    private var instance: AudioPlayer? = null

    fun get(context: Context): AudioPlayer {
        return instance ?: synchronized(this) {
            instance ?: AndroidAudioPlayer(context.applicationContext).also {
                instance = it
            }
        }
    }

    /**
     * For testing purposes to inject test doubles.
     */
    fun setForTesting(player: AudioPlayer?) {
        instance = player
    }
}
