package com.example.core.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState as FrameworkPlaybackState
import android.os.Build
import android.os.SystemClock
import com.example.core.database.model.RecordingEntity
import com.example.core.service.PlaybackService
import com.example.core.service.RecordingService
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
import kotlinx.coroutines.launch
import java.io.File

/**
 * Standard Android Framework MediaPlayer implementation for audio playback.
 * Integrates native MediaSession, Audio Focus with ducking, Foreground PlaybackService,
 * Variable Playback Speed, Sleep Timer, Repeat, and Shuffle.
 */
class AndroidAudioPlayer(
    context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : AudioPlayer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var isDucked = false
    private var resumeOnFocusGain = false
    private var originalPlaylist: List<RecordingEntity> = emptyList()
    private var activePlaylist: List<RecordingEntity> = emptyList()
    private var savedSpeedBeforeFastForward: Float? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        synchronized(this) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    pause()
                    abandonAudioFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    val wasPlaying = _playbackState.value.isPlaying
                    resumeOnFocusGain = wasPlaying
                    pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (_playbackState.value.isPlaying) {
                        try {
                            mediaPlayer?.setVolume(0.2f, 0.2f)
                            isDucked = true
                        } catch (ignored: Exception) {
                        }
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (isDucked) {
                        try {
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                        } catch (ignored: Exception) {
                        }
                        isDucked = false
                    }
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        resume()
                    }
                }
            }
        }
    }

    init {
        initMediaSession()
    }

    private fun initMediaSession() {
        if (mediaSession != null) return
        try {
            val session = MediaSession(appContext, "RecovoPlaybackSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        resume()
                    }

                    override fun onPause() {
                        pause()
                    }

                    override fun onStop() {
                        stop()
                    }

                    override fun onSkipToNext() {
                        playNext()
                    }

                    override fun onSkipToPrevious() {
                        playPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        seekTo(pos)
                    }

                    override fun onFastForward() {
                        seekRelative(10000L)
                    }

                    override fun onRewind() {
                        seekRelative(-10000L)
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
            }
            mediaSession = session
        } catch (ignored: Exception) {
        }
    }

    fun getSessionToken(): MediaSession.Token? = mediaSession?.sessionToken

    @Synchronized
    override fun setPlaylist(recordings: List<RecordingEntity>) {
        originalPlaylist = recordings
        if (_playbackState.value.isShuffleEnabled) {
            val current = _playbackState.value.currentRecording
            if (current != null) {
                val remaining = originalPlaylist.filter { it.id != current.id }.shuffled()
                activePlaylist = listOf(current) + remaining
            } else {
                activePlaylist = originalPlaylist.shuffled()
            }
        } else {
            activePlaylist = originalPlaylist
        }
    }

    @Synchronized
    override fun play(recording: RecordingEntity) {
        if (RecordingService.isRecordingActive()) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Cannot start playback while recording is in progress"
            )
            updateMediaSessionState(
                state = FrameworkPlaybackState.STATE_PAUSED,
                position = 0L,
                errorMessage = "Cannot start playback while recording is in progress"
            )
            return
        }

        val file = File(recording.filePath)
        if (!file.exists()) {
            updateMediaSessionState(
                state = FrameworkPlaybackState.STATE_ERROR,
                position = 0L,
                errorMessage = "Audio file missing from storage"
            )
            _playbackState.value = _playbackState.value.copy(
                currentRecording = recording,
                isPlaying = false,
                errorMessage = "Audio file missing from storage"
            )
            return
        }
        if (!file.isFile || file.length() == 0L) {
            updateMediaSessionState(
                state = FrameworkPlaybackState.STATE_ERROR,
                position = 0L,
                errorMessage = "Audio file is corrupted or empty (0 bytes)"
            )
            _playbackState.value = _playbackState.value.copy(
                currentRecording = recording,
                isPlaying = false,
                errorMessage = "Audio file is corrupted or empty (0 bytes)"
            )
            return
        }

        if (activePlaylist.none { it.id == recording.id }) {
            originalPlaylist = listOf(recording)
            activePlaylist = listOf(recording)
        }

        stopInternal()
        if (!requestAudioFocus()) {
            _playbackState.value = _playbackState.value.copy(
                currentRecording = recording,
                isPlaying = false,
                isPrepared = false,
                errorMessage = "Audio focus denied by system"
            )
            updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, 0L, "Audio focus denied")
            return
        }
        startPlaybackService()

        _playbackState.value = _playbackState.value.copy(
            currentRecording = recording,
            isPlaying = false,
            isPrepared = false,
            errorMessage = null
        )

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    synchronized(this@AndroidAudioPlayer) {
                        if (mediaPlayer !== mp) {
                            return@setOnPreparedListener
                        }
                        val actualDuration = if (mp.duration > 0) mp.duration.toLong() else recording.durationMs
                        _playbackState.value = _playbackState.value.copy(
                            currentRecording = recording,
                            isPlaying = true,
                            currentPositionMs = 0L,
                            durationMs = actualDuration,
                            isPrepared = true,
                            errorMessage = null
                        )

                        // Apply current playback speed
                        applySpeedToPlayer(mp, _playbackState.value.playbackSpeed)

                        updateMediaSessionMetadata(recording, actualDuration)
                        updateMediaSessionState(FrameworkPlaybackState.STATE_PLAYING, 0L)

                        try {
                            mp.start()
                            startTicker()
                        } catch (e: Exception) {
                            stopInternal()
                            _playbackState.value = _playbackState.value.copy(
                                isPlaying = false,
                                isPrepared = false,
                                errorMessage = "Unable to start playback: ${e.message}"
                            )
                            updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, 0L, e.message)
                        }
                    }
                }
                setOnCompletionListener { mp ->
                    synchronized(this@AndroidAudioPlayer) {
                        if (mediaPlayer !== mp) {
                            return@setOnCompletionListener
                        }
                        handlePlaybackCompletion()
                    }
                }
                setOnErrorListener { mp, what, extra ->
                    synchronized(this@AndroidAudioPlayer) {
                        if (mediaPlayer !== mp) {
                            return@setOnErrorListener true
                        }
                        stopTicker()
                        abandonAudioFocus()
                        stopInternal()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            isPrepared = false,
                            errorMessage = "Playback error occurred (code: $what, extra: $extra)"
                        )
                        updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, 0L, "Playback error")
                        true
                    }
                }
                prepareAsync()
            }
            mediaPlayer = player
            mediaSession?.isActive = true
        } catch (e: Exception) {
            stopInternal()
            _playbackState.value = _playbackState.value.copy(
                currentRecording = recording,
                isPlaying = false,
                isPrepared = false,
                errorMessage = "Failed to initialize player: ${e.message}"
            )
            updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, 0L, e.message)
        }
    }

    @Synchronized
    override fun updateCurrentRecordingMetadata(recording: RecordingEntity) {
        val current = _playbackState.value.currentRecording
        if (current?.id == recording.id) {
            _playbackState.value = _playbackState.value.copy(currentRecording = recording)
            updateMediaSessionMetadata(recording, _playbackState.value.durationMs)
        }
    }

    private fun handlePlaybackCompletion() {
        when (_playbackState.value.repeatMode) {
            RepeatMode.ONE -> {
                try {
                    mediaPlayer?.seekTo(0)
                    mediaPlayer?.start()
                    startTicker()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = true,
                        currentPositionMs = 0L
                    )
                    updateMediaSessionState(FrameworkPlaybackState.STATE_PLAYING, 0L)
                } catch (e: Exception) {
                    playNext()
                }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val current = _playbackState.value.currentRecording
                val list = activePlaylist
                val index = list.indexOfFirst { it.id == current?.id }
                if (index != -1 && index + 1 < list.size) {
                    play(list[index + 1])
                } else {
                    stopTicker()
                    abandonAudioFocus()
                    val dur = _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = dur
                    )
                    updateMediaSessionState(FrameworkPlaybackState.STATE_PAUSED, dur)
                }
            }
        }
    }

    @Synchronized
    override fun pause() {
        val current = _playbackState.value
        resumeOnFocusGain = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (ignored: Exception) {
        }
        stopTicker()
        val pos = try {
            mediaPlayer?.currentPosition?.toLong() ?: current.currentPositionMs
        } catch (e: Exception) {
            current.currentPositionMs
        }
        _playbackState.value = current.copy(
            isPlaying = false,
            currentPositionMs = pos
        )
        updateMediaSessionState(FrameworkPlaybackState.STATE_PAUSED, pos)
    }

    @Synchronized
    override fun resume() {
        val current = _playbackState.value
        if (current.currentRecording == null) return

        if (RecordingService.isRecordingActive()) {
            _playbackState.value = current.copy(
                errorMessage = "Cannot start playback while recording is in progress"
            )
            updateMediaSessionState(
                state = FrameworkPlaybackState.STATE_PAUSED,
                position = current.currentPositionMs,
                errorMessage = "Cannot start playback while recording is in progress"
            )
            return
        }

        if (!current.isPrepared || mediaPlayer == null) {
            play(current.currentRecording)
            return
        }

        if (!requestAudioFocus()) {
            _playbackState.value = current.copy(
                isPlaying = false,
                errorMessage = "Audio focus denied by system"
            )
            updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, current.currentPositionMs, "Audio focus denied")
            return
        }
        startPlaybackService()

        try {
            // Loop back if at or beyond end
            if (current.currentPositionMs >= current.durationMs && current.durationMs > 0L) {
                mediaPlayer?.seekTo(0)
            }
            mediaPlayer?.start()
            startTicker()
            _playbackState.value = current.copy(isPlaying = true)
            mediaSession?.isActive = true
            updateMediaSessionState(FrameworkPlaybackState.STATE_PLAYING, current.currentPositionMs)
        } catch (e: Exception) {
            _playbackState.value = current.copy(
                isPlaying = false,
                errorMessage = "Failed to resume audio: ${e.message}"
            )
            updateMediaSessionState(FrameworkPlaybackState.STATE_ERROR, current.currentPositionMs, e.message)
        }
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        val current = _playbackState.value
        val clamped = positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(0L))
        try {
            if (current.isPrepared && mediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer?.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
                } else {
                    mediaPlayer?.seekTo(clamped.toInt())
                }
            }
            _playbackState.value = current.copy(currentPositionMs = clamped)
            val sessionState = if (current.isPlaying) FrameworkPlaybackState.STATE_PLAYING else FrameworkPlaybackState.STATE_PAUSED
            updateMediaSessionState(sessionState, clamped)
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    override fun seekRelative(offsetMs: Long) {
        val current = _playbackState.value
        val target = (current.currentPositionMs + offsetMs).coerceIn(0L, current.durationMs.coerceAtLeast(0L))
        seekTo(target)
    }

    @Synchronized
    override fun playNext() {
        val current = _playbackState.value.currentRecording ?: return
        val list = activePlaylist
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1) {
            if (index + 1 < list.size) {
                play(list[index + 1])
            } else if (_playbackState.value.repeatMode == RepeatMode.ALL && list.isNotEmpty()) {
                play(list[0])
            }
        }
    }

    @Synchronized
    override fun playPrevious() {
        if (_playbackState.value.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }

        val current = _playbackState.value.currentRecording ?: return
        val list = activePlaylist
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) {
            play(list[index - 1])
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && list.isNotEmpty()) {
            play(list[list.size - 1])
        } else {
            seekTo(0L)
        }
    }

    @Synchronized
    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
        mediaPlayer?.let { mp ->
            if (_playbackState.value.isPrepared) {
                applySpeedToPlayer(mp, clamped)
            }
        }
        val sessionState = if (_playbackState.value.isPlaying) FrameworkPlaybackState.STATE_PLAYING else FrameworkPlaybackState.STATE_PAUSED
        updateMediaSessionState(sessionState, _playbackState.value.currentPositionMs)
    }

    private fun applySpeedToPlayer(player: MediaPlayer, speed: Float) {
        try {
            val wasPlaying = player.isPlaying
            val params = player.playbackParams ?: PlaybackParams()
            params.speed = speed
            player.playbackParams = params
            if (!wasPlaying && player.isPlaying) {
                player.pause()
            }
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    override fun startTemporaryFastForward() {
        if (savedSpeedBeforeFastForward == null) {
            savedSpeedBeforeFastForward = _playbackState.value.playbackSpeed
            setSpeed(2.0f)
        }
    }

    @Synchronized
    override fun stopTemporaryFastForward() {
        val target = savedSpeedBeforeFastForward ?: 1.0f
        savedSpeedBeforeFastForward = null
        setSpeed(target)
    }

    @Synchronized
    override fun setRepeatMode(mode: RepeatMode) {
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    @Synchronized
    override fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    @Synchronized
    override fun setShuffleEnabled(enabled: Boolean) {
        if (_playbackState.value.isShuffleEnabled == enabled) return
        _playbackState.value = _playbackState.value.copy(isShuffleEnabled = enabled)
        if (enabled) {
            val current = _playbackState.value.currentRecording
            if (current != null) {
                val remaining = originalPlaylist.filter { it.id != current.id }.shuffled()
                activePlaylist = listOf(current) + remaining
            } else {
                activePlaylist = originalPlaylist.shuffled()
            }
        } else {
            activePlaylist = originalPlaylist
        }
    }

    @Synchronized
    override fun toggleShuffle() {
        setShuffleEnabled(!_playbackState.value.isShuffleEnabled)
    }

    @Synchronized
    override fun setSleepTimer(durationMinutes: Int?) {
        sleepTimerJob?.cancel()
        if (durationMinutes == null || durationMinutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = null)
            return
        }

        val durationMs = durationMinutes * 60 * 1000L
        val targetEndTime = SystemClock.elapsedRealtime() + durationMs
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = durationMs)

        sleepTimerJob = coroutineScope.launch {
            while (isActive) {
                delay(1000L)
                val remaining = targetEndTime - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = null)
                    pause()
                    break
                } else {
                    _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = remaining)
                }
            }
        }
    }

    @Synchronized
    override fun cancelSleepTimer() {
        setSleepTimer(null)
    }

    @Synchronized
    override fun stop() {
        stopInternal()
        cancelSleepTimer()
        _playbackState.value = _playbackState.value.copy(
            currentRecording = null,
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = 0L,
            isPrepared = false,
            errorMessage = null
        )
        updateMediaSessionState(FrameworkPlaybackState.STATE_STOPPED, 0L)
        mediaSession?.isActive = false
    }

    @Synchronized
    private fun stopInternal() {
        stopTicker()
        abandonAudioFocus()
        try {
            val player = mediaPlayer
            mediaPlayer = null
            if (player?.isPlaying == true) {
                player.stop()
            }
            player?.reset()
            player?.release()
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    override fun release() {
        stopInternal()
        cancelSleepTimer()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        coroutineScope.cancel()
        _playbackState.value = PlaybackState()
    }

    override fun clearError() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = coroutineScope.launch {
            while (isActive) {
                delay(100)
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        val currentPos = player.currentPosition.toLong()
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = currentPos
                        )
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateMediaSessionMetadata(recording: RecordingEntity, durationMs: Long) {
        try {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, recording.displayName)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Recovo Audio Studio")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
            mediaSession?.setMetadata(metadata)
        } catch (ignored: Exception) {
        }
    }

    private fun updateMediaSessionState(state: Int, position: Long, errorMessage: String? = null) {
        try {
            val actions = FrameworkPlaybackState.ACTION_PLAY or
                FrameworkPlaybackState.ACTION_PAUSE or
                FrameworkPlaybackState.ACTION_PLAY_PAUSE or
                FrameworkPlaybackState.ACTION_STOP or
                FrameworkPlaybackState.ACTION_SEEK_TO or
                FrameworkPlaybackState.ACTION_FAST_FORWARD or
                FrameworkPlaybackState.ACTION_REWIND or
                FrameworkPlaybackState.ACTION_SKIP_TO_NEXT or
                FrameworkPlaybackState.ACTION_SKIP_TO_PREVIOUS

            val currentSpeed = _playbackState.value.playbackSpeed
            val builder = FrameworkPlaybackState.Builder()
                .setActions(actions)
                .setState(
                    state,
                    position,
                    if (state == FrameworkPlaybackState.STATE_PLAYING) currentSpeed else 0.0f,
                    SystemClock.elapsedRealtime()
                )

            if (errorMessage != null) {
                builder.setErrorMessage(errorMessage)
            }

            mediaSession?.setPlaybackState(builder.build())
        } catch (ignored: Exception) {
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = PlaybackService.startServiceIntent(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = focusReq
                audioManager?.requestAudioFocus(focusReq) ?: AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) ?: AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (ignored: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (ignored: Exception) {
        } finally {
            audioFocusRequest = null
            isDucked = false
            resumeOnFocusGain = false
        }
    }
}
