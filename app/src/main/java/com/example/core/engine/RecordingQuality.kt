package com.example.core.engine

/**
 * Practical, hardware-reliable recording quality presets for Android 11+ (API 30–36).
 * Uses native AAC in MP4 container (M4A) supported universally across all OEMs including Samsung.
 */
enum class RecordingQuality(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val sampleRate: Int,
    val bitRate: Int,
    val channelCount: Int
) {
    STANDARD(
        id = "standard",
        title = "Standard Quality",
        subtitle = "96 kbps • 44.1 kHz • Mono",
        description = "Optimized for voice notes, speech clarity, and compact file size.",
        sampleRate = 44100,
        bitRate = 96000,
        channelCount = 1
    ),
    HIGH(
        id = "high",
        title = "High Quality",
        subtitle = "192 kbps • 44.1 kHz • Mono",
        description = "Balanced clarity and fidelity, ideal for general recording and interviews.",
        sampleRate = 44100,
        bitRate = 192000,
        channelCount = 1
    ),
    MAXIMUM(
        id = "maximum",
        title = "Maximum Quality",
        subtitle = "256 kbps • 48 kHz • Mono",
        description = "Studio-grade fidelity for lectures, acoustic audio, and detailed meetings.",
        sampleRate = 48000,
        bitRate = 256000,
        channelCount = 1
    );

    fun toAudioConfig(): AudioConfig {
        return AudioConfig(
            format = RecordingFormat.M4A,
            sampleRate = sampleRate,
            bitRate = bitRate,
            channelCount = channelCount
        )
    }

    companion object {
        val DEFAULT = HIGH

        fun fromId(id: String?): RecordingQuality {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
