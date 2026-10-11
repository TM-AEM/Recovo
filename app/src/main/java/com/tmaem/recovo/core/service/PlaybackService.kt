package com.tmaem.recovo.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.tmaem.recovo.MainActivity
import com.tmaem.recovo.R
import com.tmaem.recovo.core.player.AudioPlayer
import com.tmaem.recovo.core.player.AudioPlayerProvider
import com.tmaem.recovo.core.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var audioPlayer: AudioPlayer
    private var stateJob: Job? = null
    private var isReceiverRegistered = false
    private var isForeground = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                audioPlayer.pause()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "recovo_playback_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.tmaem.recovo.playback.START"
        const val ACTION_PAUSE = "com.tmaem.recovo.playback.PAUSE"
        const val ACTION_RESUME = "com.tmaem.recovo.playback.RESUME"
        const val ACTION_STOP = "com.tmaem.recovo.playback.STOP"
        const val ACTION_NEXT = "com.tmaem.recovo.playback.NEXT"
        const val ACTION_PREV = "com.tmaem.recovo.playback.PREV"

        fun startServiceIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
            }
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
        }

        fun resumeIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_RESUME
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun nextIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_NEXT
            }
        }

        fun prevIntent(context: Context): Intent {
            return Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PREV
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioPlayer = AudioPlayerProvider.get(applicationContext)

        stateJob = serviceScope.launch {
            audioPlayer.playbackState.collect { state ->
                handlePlaybackStateChange(state)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Ensure immediate foreground transition if a recording is currently active
                val currentState = audioPlayer.playbackState.value
                if (currentState.currentRecording != null) {
                    handlePlaybackStateChange(currentState)
                }
            }
            ACTION_PAUSE -> audioPlayer.pause()
            ACTION_RESUME -> audioPlayer.resume()
            ACTION_STOP -> audioPlayer.stop()
            ACTION_NEXT -> audioPlayer.playNext()
            ACTION_PREV -> audioPlayer.playPrevious()
        }
        return START_NOT_STICKY
    }

    private fun handlePlaybackStateChange(state: PlaybackState) {
        val recording = state.currentRecording
        if (recording == null) {
            unregisterNoisyReceiver()
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            stopSelf()
            return
        }

        val notification = buildMediaNotification(state)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (!isForeground || state.isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForeground = true
            } catch (e: Exception) {
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        }

        if (state.isPlaying) {
            registerNoisyReceiver()
        } else {
            unregisterNoisyReceiver()
            // On pause, update the notification with Play button
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildMediaNotification(state: PlaybackState): Notification {
        val recording = state.currentRecording
        val isPlaying = state.isPlaying

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action
        val prevPending = PendingIntent.getService(
            this,
            1,
            prevIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevAction = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            getString(R.string.playback_notification_previous),
            prevPending
        ).build()

        // Play / Pause Action
        val playPauseAction = if (isPlaying) {
            val pausePending = PendingIntent.getService(
                this,
                2,
                pauseIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                getString(R.string.pause_recording),
                pausePending
            ).build()
        } else {
            val resumePending = PendingIntent.getService(
                this,
                3,
                resumeIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_play),
                getString(R.string.resume_recording),
                resumePending
            ).build()
        }

        // Next Action
        val nextPending = PendingIntent.getService(
            this,
            4,
            nextIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextAction = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next),
            getString(R.string.playback_notification_next),
            nextPending
        ).build()

        // Stop Action
        val stopPending = PendingIntent.getService(
            this,
            5,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            getString(R.string.playback_notification_stop),
            stopPending
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        (audioPlayer as? com.tmaem.recovo.core.player.AndroidAudioPlayer)?.getSessionToken()?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setContentTitle(recording?.displayName ?: getString(R.string.playback_notification_default_title))
            .setContentText(getString(R.string.playback_notification_content_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .build()
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                ContextCompat.registerReceiver(
                    this,
                    becomingNoisyReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            } catch (ignored: Exception) {
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (ignored: Exception) {
            } finally {
                isReceiverRegistered = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_playback_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterNoisyReceiver()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
