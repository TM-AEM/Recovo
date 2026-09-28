package com.example.feature.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.engine.RecordingPreferences
import com.example.core.engine.RecordingQuality
import com.example.core.engine.RecordingState
import com.example.core.service.RecordingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
