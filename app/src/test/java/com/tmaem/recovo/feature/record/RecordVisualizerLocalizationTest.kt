package com.tmaem.recovo.feature.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tmaem.recovo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that the recording-visualizer caption labels resolve from Android string resources
 * (rather than hardcoded literals) under both the default (English) and Arabic locales.
 *
 * The visualizer composables read these resources via `stringResource(...)`; this test asserts
 * the resource layer directly so the labels are guaranteed to be localizable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordVisualizerLocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun levelLabels_resolveFromResources() {
        assertEquals("Input Level: -12 dB", str(R.string.visualizer_input_level_db, -12))
        assertEquals("Input Paused", str(R.string.visualizer_input_paused))
        assertEquals("Input Standby", str(R.string.visualizer_input_standby))
    }

    @Test
    fun estimatedSizeBadge_formatsThroughResource() {
        assertEquals("Est. Size: ~1.5 MB • 128 kbps", str(R.string.visualizer_estimated_size, "1.5 MB", 128))
    }

    @Test
    @Config(qualifiers = "ar")
    fun levelLabels_resolveInArabic() {
        val paused = str(R.string.visualizer_input_paused)
        val standby = str(R.string.visualizer_input_standby)
        assertTrue(paused.isNotBlank())
        assertTrue(standby.isNotBlank())
        assertNotEquals("Input Paused", paused)
        assertNotEquals("Input Standby", standby)
    }
}
