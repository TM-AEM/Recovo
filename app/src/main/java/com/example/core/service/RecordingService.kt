package com.example.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.engine.AudioConfig
import com.example.core.engine.AudioRecordingEngine
import com.example.core.engine.MediaRecorderEngine
import com.example.core.engine.RecordingState
import com.example.core.storage.StorageManager
import com.example.core.storage.StorageResult
import com.example.data.repository.RecordingRepository
import com.example.data.repository.RecordingRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var storageManager: StorageManager
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var recordingEngine: MediaRecorderEngine

    private var tickerJob: Job? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    companion object {
        const val CHANNEL_ID = "recovo_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.recovo.action.START"
        const val ACTION_PAUSE = "com.example.recovo.action.PAUSE"
        const val ACTION_RESUME = "com.example.recovo.action.RESUME"
        const val ACTION_STOP = "com.example.recovo.action.STOP"
        const val ACTION_CANCEL = "com.example.recovo.action.CANCEL"

        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun startRecordingIntent(context: Context, displayName: String? = null): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, RecordingService::class.java).apply { action = ACTION_PAUSE }
        }

        fun resumeIntent(context: Context): Intent {
            return Intent(context, RecordingService::class.java).apply { action = ACTION_RESUME }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
        }

        fun cancelIntent(context: Context): Intent {
            return Intent(context, RecordingService::class.java).apply { action = ACTION_CANCEL }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        storageManager = StorageManager(applicationContext)
        val database = RecovoDatabase.getInstance(applicationContext)
        recordingRepository = RecordingRepositoryImpl(
            recordingDao = database.recordingDao(),
            folderDao = database.folderDao(),
            tagDao = database.tagDao(),
            bookmarkDao = database.bookmarkDao(),
            storageManager = storageManager
        )
        recordingEngine = MediaRecorderEngine(applicationContext)

        serviceScope.launch {
            recordingEngine.state.collect { engineState ->
                _recordingState.value = engineState
                updateNotificationForState(engineState)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)

        when (action) {
            ACTION_START -> startRecording(displayName)
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_CANCEL -> cancelRecording()
        }

        return START_NOT_STICKY
    }

    fun startRecording(customName: String? = null) {
        serviceScope.launch {
            val currentState = _recordingState.value
            if (currentState !is RecordingState.Idle && currentState !is RecordingState.Saved && currentState !is RecordingState.Error) {
                return@launch
            }

            val fileResult = storageManager.createRecordingFile(desiredName = customName, extension = "m4a")
            if (fileResult !is StorageResult.Success) {
                val errorMsg = if (fileResult is StorageResult.Error) fileResult.message else "Failed to create recording file"
                _recordingState.value = RecordingState.Error(errorMsg)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val targetFile = fileResult.data

            // Promote to Foreground Service BEFORE starting the microphone to comply with Android 11+ & 14+
            val initialNotification = buildNotification("Recording...", "00:00", isPaused = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            val startResult = recordingEngine.start(targetFile, AudioConfig())
            if (startResult.isSuccess) {
                startTimerAndMeter()
            } else {
                stopTimerAndMeter()
                targetFile.delete()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun pauseRecording() {
        serviceScope.launch {
            recordingEngine.pause()
        }
    }

    fun resumeRecording() {
        serviceScope.launch {
            recordingEngine.resume()
        }
    }

    fun stopRecording() {
        serviceScope.launch {
            stopTimerAndMeter()
            val stopResult = recordingEngine.stop()
            if (stopResult.isSuccess) {
                val session = stopResult.getOrThrow()
                // Save RecordingEntity to Room
                val entity = RecordingEntity(
                    fileName = session.file.name,
                    displayName = session.file.nameWithoutExtension,
                    filePath = session.file.absolutePath,
                    mimeType = session.mimeType,
                    format = session.format,
                    durationMs = session.durationMs,
                    fileSizeBytes = session.fileSizeBytes,
                    sampleRate = session.sampleRate,
                    bitRate = session.bitRate,
                    channelCount = session.channelCount,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )

                try {
                    val id = recordingRepository.insertRecording(entity)
                    _recordingState.value = RecordingState.Saved(session.file, id)
                } catch (dbEx: Exception) {
                    // Safety rule: File is kept on disk even if DB save fails
                    _recordingState.value = RecordingState.Error("Audio saved to disk but database index failed: ${dbEx.message}", dbEx)
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun cancelRecording() {
        serviceScope.launch {
            stopTimerAndMeter()
            recordingEngine.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimerAndMeter() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(100) // 10 updates/second is optimal for UI and battery
                recordingEngine.updateCurrentDuration()
            }
        }
    }

    private fun stopTimerAndMeter() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateNotificationForState(state: RecordingState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        when (state) {
            is RecordingState.Recording -> {
                val formattedTime = formatElapsed(state.elapsedMs)
                notificationManager.notify(NOTIFICATION_ID, buildNotification("Recording", formattedTime, isPaused = false))
            }
            is RecordingState.Paused -> {
                val formattedTime = formatElapsed(state.elapsedMs)
                notificationManager.notify(NOTIFICATION_ID, buildNotification("Paused", formattedTime, isPaused = true))
            }
            else -> {
                // Handled in stop/cancel
            }
        }
    }

    private fun buildNotification(title: String, elapsed: String, isPaused: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isPaused) {
            val resumePending = PendingIntent.getService(
                this,
                1,
                resumeIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Resume", resumePending)
        } else {
            val pausePending = PendingIntent.getService(
                this,
                2,
                pauseIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePending)
        }

        val stopPending = PendingIntent.getService(
            this,
            3,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_save, "Save", stopPending)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recovo — $title")
            .setContentText(elapsed)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recovo Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active recording status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSecs = elapsedMs / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        stopTimerAndMeter()
        runBlocking(Dispatchers.IO) { recordingEngine.cancel() }
        serviceScope.cancel()
        super.onDestroy()
    }
}
