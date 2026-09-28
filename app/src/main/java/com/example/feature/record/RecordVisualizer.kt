package com.example.feature.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.theme.RecovoSpacing
import java.util.Locale

/**
 * Dynamic animated audio equalizer waveform visualizer.
 * Renders multiple animated bars that respond dynamically to normalized microphone amplitude.
 */
@Composable
fun DynamicWaveformVisualizer(
    amplitude: Int,
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 15
    val normalizedAmp = (amplitude.toFloat() / 32767f).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = RecovoSpacing.medium)
            .testTag("recording_waveform_visualizer"),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val middle = barCount / 2f

        for (i in 0 until barCount) {
            // Distribute height based on distance from center for a natural sound-wave envelope
            val dist = kotlin.math.abs(i - middle) / middle
            val envelope = (1f - dist * 0.5f).coerceIn(0.2f, 1f)

            val targetHeightFraction = when {
                isRecording -> {
                    val variation = if (i % 2 == 0) 1.15f else 0.85f
                    (0.12f + (normalizedAmp * 0.88f * envelope * variation)).coerceIn(0.1f, 1f)
                }
                isPaused -> 0.15f * envelope
                else -> 0.08f
            }

            val animatedFraction by animateFloatAsState(
                targetValue = targetHeightFraction,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "bar_height_$i"
            )

            val barColor = when {
                isRecording -> MaterialTheme.colorScheme.error
                isPaused -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((56 * animatedFraction).dp.coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * Real-time estimated file size badge based on audio bitrate and elapsed duration.
 */
@Composable
fun EstimatedFileSizeBadge(
    bitRate: Int,
    elapsedMs: Long,
    modifier: Modifier = Modifier
) {
    val elapsedSeconds = elapsedMs / 1000L
    val estimatedBytes = (bitRate.toLong() / 8L) * elapsedSeconds

    val formattedSize = formatFileSize(estimatedBytes)

    Surface(
        modifier = modifier.testTag("recording_estimated_size_badge"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = "Est. Size: ~$formattedSize • ${(bitRate / 1000)} kbps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = RecovoSpacing.small, vertical = 4.dp)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
