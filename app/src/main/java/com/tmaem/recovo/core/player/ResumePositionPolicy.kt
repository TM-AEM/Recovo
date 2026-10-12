package com.tmaem.recovo.core.player

/**
 * Pure, testable rules for converting between a stored resume position and a playback position.
 *
 * Representation chosen for a finished/transient recording is `null` (never `0`):
 *  - `null`  -> no saved position; playback starts at zero.
 *  - `> 0`   -> a real mid-track resume point.
 * A position equal to or beyond a known duration is treated as completed (returns null) so a
 * finished recording never resumes at end-of-file.
 */
internal object ResumePositionPolicy {

    /** How often an actively-playing track is checkpointed to the database. */
    const val CHECKPOINT_INTERVAL_MS = 2000L

    /**
     * Returns the position to seek to when starting [durationMs]-long playback given a [stored]
     * position, or null when playback should start at zero.
     *
     * Unknown duration ([durationMs] <= 0) returns null: with no upper bound we cannot validate
     * the stored value, so we deliberately do not restore it.
     */
    fun restoreTarget(stored: Long?, durationMs: Long): Long? {
        if (stored == null || stored <= 0L) return null
        if (durationMs <= 0L) return null
        if (stored >= durationMs) return null
        return stored
    }

    /**
     * Normalizes an in-memory [positionMs] for persistence against a known [durationMs].
     * Returns null for transient values that must not become a stale resume point
     * (zero/negative, unknown duration, or at/after end of track).
     */
    fun normalizeForSave(positionMs: Long, durationMs: Long): Long? {
        if (positionMs <= 0L) return null
        if (durationMs <= 0L) return null
        if (positionMs >= durationMs) return null
        return positionMs
    }
}
