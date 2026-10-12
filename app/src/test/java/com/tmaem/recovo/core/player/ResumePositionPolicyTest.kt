package com.tmaem.recovo.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePositionPolicyTest {

    @Test
    fun restoreTarget_nullPosition_startsAtZero() {
        assertNull(ResumePositionPolicy.restoreTarget(null, 10_000L))
    }

    @Test
    fun restoreTarget_zeroOrNegative_isRejected() {
        assertNull(ResumePositionPolicy.restoreTarget(0L, 10_000L))
        assertNull(ResumePositionPolicy.restoreTarget(-1L, 10_000L))
        assertNull(ResumePositionPolicy.restoreTarget(-5_000L, 10_000L))
    }

    @Test
    fun restoreTarget_validMidTrack_isReturned() {
        assertEquals(4_000L, ResumePositionPolicy.restoreTarget(4_000L, 10_000L))
    }

    @Test
    fun restoreTarget_atOrBeyondDuration_isTreatedAsCompleted() {
        assertNull(ResumePositionPolicy.restoreTarget(10_000L, 10_000L))
        assertNull(ResumePositionPolicy.restoreTarget(12_000L, 10_000L))
    }

    @Test
    fun restoreTarget_unknownDuration_doesNotRestore() {
        assertNull(ResumePositionPolicy.restoreTarget(4_000L, 0L))
        assertNull(ResumePositionPolicy.restoreTarget(4_000L, -1L))
    }

    @Test
    fun normalizeForSave_zeroNegativeOrUnknownDuration_isNull() {
        assertNull(ResumePositionPolicy.normalizeForSave(0L, 10_000L))
        assertNull(ResumePositionPolicy.normalizeForSave(-1L, 10_000L))
        assertNull(ResumePositionPolicy.normalizeForSave(4_000L, 0L))
    }

    @Test
    fun normalizeForSave_atOrBeyondDuration_isNull() {
        assertNull(ResumePositionPolicy.normalizeForSave(10_000L, 10_000L))
        assertNull(ResumePositionPolicy.normalizeForSave(11_000L, 10_000L))
    }

    @Test
    fun normalizeForSave_validMidTrack_isReturned() {
        assertEquals(4_000L, ResumePositionPolicy.normalizeForSave(4_000L, 10_000L))
    }

    @Test
    fun checkpointInterval_isSensible() {
        assertEquals(2_000L, ResumePositionPolicy.CHECKPOINT_INTERVAL_MS)
    }

    // --- Boundary cases (Task 34-6D) ---

    @Test
    fun restoreTarget_immediatelyBelowDuration_isRetained() {
        assertEquals(9_999L, ResumePositionPolicy.restoreTarget(9_999L, 10_000L))
        assertEquals(1L, ResumePositionPolicy.restoreTarget(1L, 10_000L))
    }

    @Test
    fun restoreTarget_equalToDuration_isRejected() {
        assertNull(ResumePositionPolicy.restoreTarget(10_000L, 10_000L))
    }

    @Test
    fun restoreTarget_greaterThanDuration_isRejected() {
        assertNull(ResumePositionPolicy.restoreTarget(10_001L, 10_000L))
    }

    @Test
    fun restoreTarget_immediatelyBelowDuration_withUnknownDuration_isRejected() {
        assertNull(ResumePositionPolicy.restoreTarget(9_999L, 0L))
        assertNull(ResumePositionPolicy.restoreTarget(9_999L, -1L))
    }

    @Test
    fun normalizeForSave_immediatelyBelowDuration_isRetained() {
        assertEquals(9_999L, ResumePositionPolicy.normalizeForSave(9_999L, 10_000L))
    }

    @Test
    fun normalizeForSave_equalOrGreaterThanDuration_isRejected() {
        assertNull(ResumePositionPolicy.normalizeForSave(10_000L, 10_000L))
        assertNull(ResumePositionPolicy.normalizeForSave(10_001L, 10_000L))
    }
}
