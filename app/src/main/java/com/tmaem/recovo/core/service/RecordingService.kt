package com.tmaem.recovo.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tmaem.recovo.MainActivity
import com.tmaem.recovo.R
import com.tmaem.recovo.core.database.RecovoDatabase
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.engine.AudioConfig
import com.tmaem.recovo.core.engine.AudioRecordingEngine
import com.tmaem.recovo.core.engine.MediaRecorderEngine
import com.tmaem.recovo.core.engine.RecordingPreferences
import com.tmaem.recovo.core.engine.RecordingQuality
import com.tmaem.recovo.core.engine.RecordingState
import com.tmaem.recovo.core.player.AudioPlayerProvider
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.data.repository.RecordingRepository
import com.tmaem.recovo.data.repository.RecordingRepositoryImpl
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

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseRecording(isInterrupted = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained; recording remains paused with isInterrupted=true until user explicitly resumes
            }
        }
    }

    private var tickerJob: Job? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    companion object {
        const val CHANNEL_ID = "recovo_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.tmaem.recovo.action.START"
        const val ACTION_PAUSE = "com.tmaem.recovo.action.PAUSE"
        const val ACTION_RESUME = "com.tmaem.recovo.action.RESUME"
        const val ACTION_STOP = "com.tmaem.recovo.action.STOP"
        const val ACTION_CANCEL = "com.tmaem.recovo.action.CANCEL"

        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_QUALITY_ID = "extra_quality_id"
        const val EXTRA_IS_INTERRUPTED = "extra_is_interrupted"

        @Volatile
        private var _isRecordingActive: Boolean = false

        @Volatile
        private var activeServiceInstance: RecordingService? = null

        fun isRecordingActive(): Boolean = _isRecordingActive

        fun getActiveService(): RecordingService? = activeServiceInstance

        fun setRecordingActiveForTesting(active: Boolean) {
            _isRecordingActive = active
        }

        fun startRecordingIntent(context: Context, displayName: String? = null, qualityId: String? = null): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_QUALITY_ID, qualityId)
            }
        }

        fun pauseIntent(context: Context, isInterrupted: Boolean = false): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_IS_INTERRUPTED, isInterrupted)
            }
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
        activeServiceInstance = this
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
                val current = _recordingState.value
                if (engineState is RecordingState.Idle && (current is RecordingState.Saved || current is RecordingState.Stopping)) {
                    return@collect
                }
                _recordingState.value = engineState
                _isRecordingActive = engineState is RecordingState.Recording || engineState is RecordingState.Paused || engineState is RecordingState.Preparing
                updateNotificationForState(engineState)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val qualityId = intent.getStringExtra(EXTRA_QUALITY_ID)
        val isInterrupted = intent.getBooleanExtra(EXTRA_IS_INTERRUPTED, false)

        when (action) {
            ACTION_START -> startRecording(displayName, qualityId)
            ACTION_PAUSE -> pauseRecording(isInterrupted)
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_CANCEL -> cancelRecording()
        }

        return START_NOT_STICKY
    }

    fun startRecording(customName: String? = null, qualityId: String? = null) {
        val currentState = _recordingState.value
        if (currentState !is RecordingState.Idle && currentState !is RecordingState.Saved && currentState !is RecordingState.Error) {
            return
        }

        _recordingState.value = RecordingState.Preparing
        _isRecordingActive = true

        serviceScope.launch {
            // Explicitly pause any active audio playback before starting microphone recording
            try {
                val player = AudioPlayerProvider.get(applicationContext)
                player.pause()
                player.cancelSleepTimer()
            } catch (ignored: Exception) {
            }

            // Acquire exclusive transient audio focus for microphone recording
            if (!requestAudioFocus()) {
                _recordingState.value = RecordingState.Error("Audio focus denied by system")
                _isRecordingActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val fileResult = storageManager.createRecordingFile(desiredName = customName, extension = "m4a")
            if (fileResult !is StorageResult.Success) {
                abandonAudioFocus()
                _isRecordingActive = false
                val errorMsg = if (fileResult is StorageResult.Error) fileResult.message else "Failed to create recording file"
                _recordingState.value = RecordingState.Error(errorMsg)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val targetFile = fileResult.data

            // Promote to Foreground Service BEFORE starting the microphone to comply with Android 11+ & 14+
            val initialNotification = buildNotification(getString(R.string.notification_recording_starting), "00:00", isPaused = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            val quality = if (qualityId != null) {
                RecordingQuality.fromId(qualityId)
            } else {
                RecordingPreferences(applicationContext).selectedQualityFlow.value
            }
            val config = quality.toAudioConfig()

            val startResult = recordingEngine.start(targetFile, config)
            if (startResult.isSuccess) {
                startTimerAndMeter()
            } else {
                abandonAudioFocus()
                _isRecordingActive = false
                stopTimerAndMeter()
                targetFile.delete()
                val err = startResult.exceptionOrNull()
                _recordingState.value = RecordingState.Error("Failed to start recording: ${err?.localizedMessage ?: "Unknown error"}", err)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun pauseRecording(isInterrupted: Boolean = false) {
        val currentState = _recordingState.value
        if (currentState !is RecordingState.Recording) {
            return
        }
        serviceScope.launch {
            recordingEngine.pause(isInterrupted)
        }
    }

    fun resumeRecording() {
        val currentState = _recordingState.value
        if (currentState !is RecordingState.Paused) {
            return
        }
        serviceScope.launch {
            recordingEngine.resume()
        }
    }

    fun stopRecording() {
        val currentState = _recordingState.value
        if (currentState !is RecordingState.Recording && currentState !is RecordingState.Paused) {
            return
        }
        _recordingState.value = RecordingState.Stopping
        stopTimerAndMeter()
        abandonAudioFocus()
        _isRecordingActive = false

        serviceScope.launch {
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
            } else {
                val stopEx = stopResult.exceptionOrNull()
                _recordingState.value = RecordingState.Error("Failed to finalize recording: ${stopEx?.message ?: "Unknown error"}", stopEx)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun cancelRecording() {
        val currentState = _recordingState.value
        if (currentState !is RecordingState.Recording && currentState !is RecordingState.Paused && currentState !is RecordingState.Preparing) {
            return
        }
        _recordingState.value = RecordingState.Idle
        stopTimerAndMeter()
        abandonAudioFocus()
        _isRecordingActive = false

        serviceScope.launch {
            try {
                recordingEngine.cancel()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val am = audioManager ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = focusReq
                val res = am.requestAudioFocus(focusReq)
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (ignored: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (ignored: Exception) {
        } finally {
            audioFocusRequest = null
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
                notificationManager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_recording_active), formattedTime, isPaused = false))
            }
            is RecordingState.Paused -> {
                val formattedTime = formatElapsed(state.elapsedMs)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_recording_paused), formattedTime, isPaused = true))
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
            NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.resume_recording), resumePending)
        } else {
            val pausePending = PendingIntent.getService(
                this,
                2,
                pauseIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause_recording), pausePending)
        }

        val stopPending = PendingIntent.getService(
            this,
            3,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_save, getString(R.string.save), stopPending)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_content_title, title))
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
                getString(R.string.notification_recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_recording_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSecs = (elapsedMs / 1000).coerceAtLeast(0)
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        abandonAudioFocus()
        _isRecordingActive = false
        stopTimerAndMeter()
        val current = _recordingState.value
        if (current !is RecordingState.Saved && current !is RecordingState.Stopping) {
            runBlocking(Dispatchers.IO) { recordingEngine.cancel() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
