package com.example.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.engine.RecordingState
import com.example.core.service.RecordingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RecordViewModel(
    private val controller: RecordingController
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = controller.recordingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecordingState.Idle
        )

    fun startRecording(customName: String? = null) {
        controller.startRecording(customName)
    }

    fun pauseRecording() {
        controller.pauseRecording()
    }

    fun resumeRecording() {
        controller.resumeRecording()
    }

    fun stopRecording() {
        controller.stopRecording()
    }

    fun cancelRecording() {
        controller.cancelRecording()
    }

    fun resetState() {
        controller.resetStateToIdle()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
