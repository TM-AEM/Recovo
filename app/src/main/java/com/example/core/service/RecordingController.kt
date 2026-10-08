package com.example.core.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.example.core.engine.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller bridging UI / ViewModels to the Foreground [RecordingService].
 * Survives Composable recomposition and Activity recreation.
 */
class RecordingController(val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var service: RecordingService? = null

    // True while the ServiceConnection is registered via bindService(). Set on the successful
    // bind call (before onServiceConnected fires) so release() always unbinds exactly once.
    private var isBound = false

    private val _recordingState = MutableStateFlow<RecordingState>(
        RecordingService.getActiveService()?.recordingState?.value ?: RecordingState.Idle
    )
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var stateCollectionJob: kotlinx.coroutines.Job? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? RecordingService.LocalBinder
            service = localBinder?.getService()
            isBound = true

            service?.let { svc ->
                stateCollectionJob?.cancel()
                stateCollectionJob = scope.launch {
                    svc.recordingState.collect { state ->
                        _recordingState.value = state
                    }
                }
            }

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            service = null
            isBound = false
            val current = _recordingState.value
            if (current is RecordingState.Recording || current is RecordingState.Paused || current is RecordingState.Preparing) {
                _recordingState.value = RecordingState.Error("Recording service disconnected unexpectedly")
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            service = null
            isBound = false
            val current = _recordingState.value
            if (current is RecordingState.Recording || current is RecordingState.Paused || current is RecordingState.Preparing) {
                _recordingState.value = RecordingState.Error("Recording service connection died")
            }
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        if (isBound) {
            return
        }
        val intent = Intent(context, RecordingService::class.java)
        if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            isBound = true
        }
    }

    fun startRecording(displayName: String? = null, qualityId: String? = null) {
        val current = _recordingState.value
        if (current !is RecordingState.Idle && current !is RecordingState.Saved && current !is RecordingState.Error) {
            return
        }
        _recordingState.value = RecordingState.Preparing
        val intent = RecordingService.startRecordingIntent(context, displayName, qualityId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        bindToService()
    }

    fun pauseRecording(isInterrupted: Boolean = false) {
        val current = _recordingState.value
        if (current !is RecordingState.Recording) {
            return
        }
        service?.pauseRecording(isInterrupted) ?: run {
            val intent = RecordingService.pauseIntent(context, isInterrupted)
            context.startService(intent)
        }
    }

    fun resumeRecording() {
        val current = _recordingState.value
        if (current !is RecordingState.Paused) {
            return
        }
        service?.resumeRecording() ?: run {
            val intent = RecordingService.resumeIntent(context)
            context.startService(intent)
        }
    }

    fun stopRecording() {
        val current = _recordingState.value
        if (current !is RecordingState.Recording && current !is RecordingState.Paused) {
            return
        }
        _recordingState.value = RecordingState.Stopping
        service?.stopRecording() ?: run {
            val intent = RecordingService.stopIntent(context)
            context.startService(intent)
        }
    }

    fun cancelRecording() {
        val current = _recordingState.value
        if (current !is RecordingState.Recording && current !is RecordingState.Paused && current !is RecordingState.Preparing) {
            return
        }
        _recordingState.value = RecordingState.Idle
        service?.cancelRecording() ?: run {
            val intent = RecordingService.cancelIntent(context)
            context.startService(intent)
        }
    }

    fun resetStateToIdle() {
        _recordingState.value = RecordingState.Idle
    }

    fun unbind() {
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (ignored: Exception) {
            }
        }
        isBound = false
        service = null
    }

    fun release() {
        unbind()
        scope.cancel()
    }
}
