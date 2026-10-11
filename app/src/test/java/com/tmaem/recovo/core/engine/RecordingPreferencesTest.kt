package com.tmaem.recovo.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: RecordingPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences for clean isolated test
        context.getSharedPreferences("recovo_recording_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = RecordingPreferences(context)
    }

    @Test
    fun defaultQuality_isHigh() {
        assertEquals(RecordingQuality.HIGH, preferences.getSelectedQuality())
        assertEquals(RecordingQuality.HIGH, preferences.selectedQualityFlow.value)
    }

    @Test
    fun setSelectedQuality_persistsAndUpdatesFlow() {
        preferences.setSelectedQuality(RecordingQuality.MAXIMUM)
        assertEquals(RecordingQuality.MAXIMUM, preferences.getSelectedQuality())
        assertEquals(RecordingQuality.MAXIMUM, preferences.selectedQualityFlow.value)

        // Verify fresh instance reads the persisted value from disk
        val freshInstance = RecordingPreferences(context)
        assertEquals(RecordingQuality.MAXIMUM, freshInstance.getSelectedQuality())
        assertEquals(RecordingQuality.MAXIMUM, freshInstance.selectedQualityFlow.value)
    }

    @Test
    fun setSelectedQuality_standardQuality_persistsCorrectly() {
        preferences.setSelectedQuality(RecordingQuality.STANDARD)
        assertEquals(RecordingQuality.STANDARD, preferences.getSelectedQuality())
        assertEquals(RecordingQuality.STANDARD, preferences.selectedQualityFlow.value)

        val freshInstance = RecordingPreferences(context)
        assertEquals(RecordingQuality.STANDARD, freshInstance.getSelectedQuality())
    }
}
