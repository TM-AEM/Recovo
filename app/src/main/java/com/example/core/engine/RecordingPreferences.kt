package com.example.core.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight local preference manager for recording quality and settings.
 * Uses native SharedPreferences with zero external dependencies.
 */
class RecordingPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedQualityFlow = MutableStateFlow(getSelectedQuality())
    val selectedQualityFlow: StateFlow<RecordingQuality> = _selectedQualityFlow.asStateFlow()

    fun getSelectedQuality(): RecordingQuality {
        val qualityId = prefs.getString(KEY_QUALITY, RecordingQuality.DEFAULT.id)
        return RecordingQuality.fromId(qualityId)
    }

    fun setSelectedQuality(quality: RecordingQuality) {
        prefs.edit().putString(KEY_QUALITY, quality.id).apply()
        _selectedQualityFlow.value = quality
    }

    companion object {
        private const val PREFS_NAME = "recovo_recording_preferences"
        private const val KEY_QUALITY = "key_recording_quality"
    }
}
