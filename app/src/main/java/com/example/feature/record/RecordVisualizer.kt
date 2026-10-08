package com.example.feature.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.theme.RecovoSpacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10

/**
 * Lightweight, hardware-efficient recording audio level meter.
 * Uses a single Canvas pass with zero per-frame object allocations,
 * avoiding expensive multiple Compose animations while maintaining
 * high readability across Samsung and Android 11+ devices.
 */
@Composable
fun DynamicWaveformVisualizer(
    amplitude: Int,
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val targetLevel = when {
        isRecording -> (amplitude.toFloat() / 32767f).coerceIn(0f, 1f)
        else -> 0f
    }

    val animatedLevel by animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "level_meter_anim"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RecovoSpacing.medium)
            .testTag("recording_waveform_visualizer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("recording_level_meter")
        ) {
            val barCount = 19
            val spacing = 4.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(3.dp.toPx())
            val maxBarHeight = size.height
            val minBarHeight = 4.dp.toPx()
            val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            val centerIndex = (barCount - 1) / 2f

            for (i in 0 until barCount) {
                val distFromCenter = abs(i - centerIndex) / centerIndex
                val envelope = (1f - distFromCenter * 0.45f).coerceIn(0.25f, 1f)
                val alternatingWeight = if (i % 2 == 0) 1.08f else 0.92f

                val barHeight = when {
                    isRecording -> {
                        val activeFraction = (animatedLevel * envelope * alternatingWeight).coerceIn(0f, 1f)
                        (minBarHeight + (maxBarHeight - minBarHeight) * activeFraction).coerceIn(minBarHeight, maxBarHeight)
                    }
                    isPaused -> minBarHeight * 1.5f * envelope
                    else -> minBarHeight
                }

                val left = i * (barWidth + spacing)
                val top = (maxBarHeight - barHeight) / 2f

                val barColor = when {
                    isRecording -> {
                        val activeThreshold = (i.toFloat() / barCount.toFloat())
                        if (animatedLevel >= activeThreshold) {
                            if (distFromCenter < 0.25f && animatedLevel > 0.65f) errorColor else primaryColor
                        } else {
                            outlineVariantColor.copy(alpha = 0.35f)
                        }
                    }
                    isPaused -> tertiaryColor.copy(alpha = 0.4f)
                    else -> surfaceVariantColor
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )
            }
        }

        Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))

        // Level caption: clean, accessible visual feedback
        val levelLabel = when {
            isRecording -> {
                val db = if (amplitude > 0) (20 * log10(amplitude.toDouble() / 32767.0)).toInt().coerceIn(-60, 0) else -60
                "Input Level: $db dB"
            }
            isPaused -> "Input Paused"
            else -> "Input Standby"
        }

        Text(
            text = levelLabel,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Normal
        )
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
