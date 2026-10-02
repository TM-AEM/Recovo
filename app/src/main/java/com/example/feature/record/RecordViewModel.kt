package com.example.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import android.content.Context
import com.example.core.engine.RecordingPreferences
import com.example.core.engine.RecordingQuality
import com.example.core.engine.RecordingState
import com.example.core.service.RecordingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RecordViewModel(
    private val controller: RecordingController,
    private val preferences: RecordingPreferences = RecordingPreferences(controller.context),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = controller.recordingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = controller.recordingState.value
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
        controller.release()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val controller = RecordingController(appContext)
            return RecordViewModel(controller) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val appContext = context.applicationContext
            val controller = RecordingController(appContext)
            val savedStateHandle = extras.createSavedStateHandle()
            return RecordViewModel(controller, savedStateHandle = savedStateHandle) as T
        }
    }
}

