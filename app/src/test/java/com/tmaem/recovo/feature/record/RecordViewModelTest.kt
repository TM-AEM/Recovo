package com.tmaem.recovo.feature.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.engine.RecordingPreferences
import com.tmaem.recovo.core.engine.RecordingQuality
import com.tmaem.recovo.core.engine.RecordingState
import com.tmaem.recovo.core.service.RecordingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordViewModelTest {

    private lateinit var context: Context
    private lateinit var controller: RecordingController
    private lateinit var preferences: RecordingPreferences
    private lateinit var viewModel: RecordViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("recovo_recording_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        controller = RecordingController(context)
        preferences = RecordingPreferences(context)
        viewModel = RecordViewModel(controller, preferences)
    }

    @org.junit.After
    fun tearDown() {
        controller.cancelRecording()
        controller.release()
        com.tmaem.recovo.core.service.RecordingService.getActiveService()?.let {
            it.stopSelf()
        }
        com.tmaem.recovo.core.service.RecordingService.setRecordingActiveForTesting(false)
    }

    @Test
    fun initialState_isIdle() {
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun initialQuality_isHigh() {
        assertEquals(RecordingQuality.HIGH, viewModel.selectedQuality.value)
    }

    @Test
    fun setQuality_updatesSelectedQuality() {
        viewModel.setQuality(RecordingQuality.MAXIMUM)
        assertEquals(RecordingQuality.MAXIMUM, viewModel.selectedQuality.value)
        assertEquals(RecordingQuality.MAXIMUM, preferences.getSelectedQuality())

        viewModel.setQuality(RecordingQuality.STANDARD)
        assertEquals(RecordingQuality.STANDARD, viewModel.selectedQuality.value)
        assertEquals(RecordingQuality.STANDARD, preferences.getSelectedQuality())
    }

    @Test
    fun resetState_returnsToIdle() {
        viewModel.resetState()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun formatTimer_underOneHour_formatsMinutesAndSeconds() {
        assertEquals("00:00", formatTimer(0L))
        assertEquals("00:01", formatTimer(1000L))
        assertEquals("01:05", formatTimer(65000L))
        assertEquals("09:59", formatTimer(599000L))
        assertEquals("59:59", formatTimer(3599000L))
    }

    @Test
    fun formatTimer_oneHourAndAbove_formatsHoursMinutesSeconds() {
        assertEquals("01:00:00", formatTimer(3600000L))
        assertEquals("01:05:00", formatTimer(3900000L))
        assertEquals("12:34:56", formatTimer(45296000L))
    }

    @Test
    fun rapidDoubleStart_preventsDuplicateSessionInitiation() {
        // Initial state is Idle
        assertTrue(controller.recordingState.value is RecordingState.Idle)

        // First start transitions to Preparing
        controller.startRecording("Session 1")
        assertTrue(controller.recordingState.value is RecordingState.Preparing)

        // Rapid second start while Preparing must be dropped by Controller guard
        controller.startRecording("Session 2")
        assertTrue(controller.recordingState.value is RecordingState.Preparing)
    }

    @Test
    fun rapidStopAndPauseCalls_whileIdle_areIdempotentAndSafe() {
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        // Calling stop or pause when not actively recording must safely no-op
        viewModel.stopRecording()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        viewModel.pauseRecording()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        viewModel.resumeRecording()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        viewModel.cancelRecording()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun recordingInterruption_preservesInterruptedSemanticState() {
        val dummyFile = File(context.cacheDir, "interruption_test.m4a")
        val interruptedState = RecordingState.Paused(
            file = dummyFile,
            elapsedMs = 5000L,
            amplitude = 0,
            isInterrupted = true
        )
        val normalPausedState = RecordingState.Paused(
            file = dummyFile,
            elapsedMs = 5000L,
            amplitude = 0,
            isInterrupted = false
        )

        assertTrue(interruptedState.isInterrupted)
        assertFalse(normalPausedState.isInterrupted)
        assertEquals(5000L, interruptedState.elapsedMs)
    }
}
