package com.tmaem.recovo.core.service

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
 * Verifies that recording/playback notification text (titles, action labels, channel
 * name/description) resolves from Android string resources under English and Arabic.
 *
 * The service builders read these via `getString(...)`; this test guards the resource layer so
 * notification labels remain localizable and the channel/action behavior is untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationLocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun recordingNotificationText_resolvesFromResources() {
        assertEquals("Recording…", str(R.string.notification_recording_starting))
        assertEquals("Recording", str(R.string.notification_recording_active))
        assertEquals("Paused", str(R.string.notification_recording_paused))
        assertEquals("Recovo — Recording", str(R.string.notification_recording_content_title, "Recording"))
        assertEquals("Recovo Recording", str(R.string.notification_recording_channel_name))
        assertTrue(str(R.string.notification_recording_channel_desc).isNotBlank())
    }

    @Test
    fun playbackNotificationText_resolvesFromResources() {
        assertEquals("Previous", str(R.string.playback_notification_previous))
        assertEquals("Next", str(R.string.playback_notification_next))
        assertEquals("Stop", str(R.string.playback_notification_stop))
        assertEquals("Audio Playback", str(R.string.notification_playback_channel_name))
        assertTrue(str(R.string.notification_playback_channel_desc).isNotBlank())
    }

    @Test
    fun sharedActionLabels_resolveFromResources() {
        assertEquals("Pause", str(R.string.pause_recording))
        assertEquals("Resume", str(R.string.resume_recording))
        assertEquals("Save", str(R.string.save))
    }

    @Test
    @Config(qualifiers = "ar")
    fun notificationText_resolvesInArabic_notEnglish() {
        val active = str(R.string.notification_recording_active)
        assertTrue(active.isNotBlank())
        assertNotEquals("Recording", active)
        assertNotEquals("Paused", str(R.string.notification_recording_paused))
        // The recording content-title format keeps the brand literal but localizes the placeholder.
        assertEquals("Recovo — التسجيل", str(R.string.notification_recording_content_title, str(R.string.notification_recording_active)))
    }
}
