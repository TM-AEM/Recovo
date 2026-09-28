package com.example.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.engine.RecordingPreferences
import com.example.core.engine.RecordingQuality
import com.example.core.engine.RecordingState
import com.example.core.service.RecordingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RecordViewModel(
    private val controller: RecordingController,
    private val preferences: RecordingPreferences = RecordingPreferences(controller.context)
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = controller.recordingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecordingState.Idle
        )

    val selectedQuality: StateFlow<RecordingQuality> = preferences.selectedQualityFlow

    fun setQuality(quality: RecordingQuality) {
        preferences.setSelectedQuality(quality)
    }

    fun startRecording(customName: String? = null) {
        controller.startRecording(customName, selectedQuality.value.id)
    }

    fun pauseRecording(isInterrupted: Boolean = false) {
        controller.pauseRecording(isInterrupted)
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

