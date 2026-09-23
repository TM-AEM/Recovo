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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller bridging UI / ViewModels to the Foreground [RecordingService].
 * Survives Composable recomposition and Activity recreation.
 */
class RecordingController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var service: RecordingService? = null
    private var isBound = false

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? RecordingService.LocalBinder
            service = localBinder?.getService()
            isBound = true

            service?.let { svc ->
                scope.launch {
                    svc.recordingState.collect { state ->
                        _recordingState.value = state
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun startRecording(displayName: String? = null) {
        val intent = RecordingService.startRecordingIntent(context, displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        if (!isBound) {
            bindToService()
        }
    }

    fun pauseRecording() {
        service?.pauseRecording() ?: run {
            val intent = RecordingService.pauseIntent(context)
            context.startService(intent)
        }
    }

    fun resumeRecording() {
        service?.resumeRecording() ?: run {
            val intent = RecordingService.resumeIntent(context)
            context.startService(intent)
        }
    }

    fun stopRecording() {
        service?.stopRecording() ?: run {
            val intent = RecordingService.stopIntent(context)
            context.startService(intent)
        }
    }

    fun cancelRecording() {
        service?.cancelRecording() ?: run {
            val intent = RecordingService.cancelIntent(context)
            context.startService(intent)
        }
    }

    fun resetStateToIdle() {
        _recordingState.value = RecordingState.Idle
    }

    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (ignored: Exception) {
            }
            isBound = false
        }
    }
}
