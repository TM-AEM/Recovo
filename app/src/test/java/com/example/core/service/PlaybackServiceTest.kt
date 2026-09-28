package com.example.core.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.player.AudioPlayerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    @Test
    fun intentFactories_createCorrectActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val start = PlaybackService.startServiceIntent(context)
        assertEquals(PlaybackService.ACTION_START, start.action)

        val pause = PlaybackService.pauseIntent(context)
        assertEquals(PlaybackService.ACTION_PAUSE, pause.action)

        val resume = PlaybackService.resumeIntent(context)
        assertEquals(PlaybackService.ACTION_RESUME, resume.action)

        val stop = PlaybackService.stopIntent(context)
        assertEquals(PlaybackService.ACTION_STOP, stop.action)

        val next = PlaybackService.nextIntent(context)
        assertEquals(PlaybackService.ACTION_NEXT, next.action)

        val prev = PlaybackService.prevIntent(context)
        assertEquals(PlaybackService.ACTION_PREV, prev.action)
    }

    @Test
    fun serviceLifecycle_createsAndDestroysCleanly() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()
        assertNotNull(service)

        val binder = service.onBind(null)
        assertNotNull(binder)

        serviceController.destroy()
    }
}
