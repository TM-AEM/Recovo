package com.tmaem.recovo.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmaem.recovo.R
import com.tmaem.recovo.core.designsystem.theme.RecovoDimensions
import com.tmaem.recovo.core.designsystem.theme.RecovoSpacing
import com.tmaem.recovo.core.player.PlaybackState
import com.tmaem.recovo.core.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenTimerDialog: () -> Unit,
    onStartFastForward: () -> Unit,
    onStopFastForward: () -> Unit,
    onClose: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recording = playbackState.currentRecording ?: return

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val currentMs = if (isDraggingSlider) dragPosition.toLong() else playbackState.currentPositionMs
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val sliderValue = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    var hudText by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val seekPositionDescription = stringResource(
        R.string.a11y_seek_position,
        LibraryViewModel.formatDuration(currentMs),
        LibraryViewModel.formatDuration(durationMs)
    )
    val fastForwardHud = stringResource(R.string.playback_hud_fast_forward)
    val seekPurpose = stringResource(R.string.a11y_playback_seek)
    val rewindHud = stringResource(R.string.playback_hud_rewind)
    val forwardHud = stringResource(R.string.playback_hud_forward)

    fun triggerHud(text: String) {
        hudText = text
        coroutineScope.launch {
            delay(800L)
            if (hudText == text) {
                hudText = null
            }
        }
    }

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("playback_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.small)
        ) {
            // Error banner if any
            playbackState.errorMessage?.let { errorMsg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = RecovoSpacing.extraSmall)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = RecovoSpacing.small, vertical = RecovoSpacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissError,
                        modifier = Modifier.size(RecovoDimensions.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.playback_cd_dismiss_error),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Header row with Gesture Zone (Double-tap left -10s, right +10s, Long-press 2x)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    onRewind()
                                    triggerHud(rewindHud)
                                } else {
                                    onForward()
                                    triggerHud(forwardHud)
                                }
                            },
                            onLongPress = {
                                onStartFastForward()
                                triggerHud(fastForwardHud)
                            },
                            onPress = {
                                tryAwaitRelease()
                                onStopFastForward()
                                if (hudText == fastForwardHud) {
                                    hudText = null
                                }
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(RecovoSpacing.small))
                        Text(
                            text = recording.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Transient HUD feedback pill
                    AnimatedVisibility(
                        visible = hudText != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = hudText ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(RecovoDimensions.minTouchTarget)
                            .testTag("playback_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.playback_cd_close_player),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Slider
            Slider(
                value = sliderValue,
                onValueChange = { frac ->
                    isDraggingSlider = true
                    dragPosition = frac * durationMs
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    onSeekTo(dragPosition.toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("playback_seek_bar")
                    .semantics {
                        contentDescription = seekPurpose
                        stateDescription = seekPositionDescription
                    },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = LibraryViewModel.formatDuration(currentMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = LibraryViewModel.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Controls Row (Shuffle, Prev, Rewind 10, Play/Pause, Forward 10, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RecovoSpacing.extraSmall),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.playback_cd_shuffle),
                        tint = if (playbackState.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Previous track
                IconButton(
                    onClick = onPlayPrevious,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_previous_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.playback_cd_previous)
                    )
                }

                // Rewind 10s
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_rewind_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.playback_cd_rewind_10)
                    )
                }

                // Play / Pause
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("playback_play_pause_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) stringResource(R.string.playback_cd_pause) else stringResource(R.string.playback_cd_play),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Forward 10s
                IconButton(
                    onClick = onForward,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = stringResource(R.string.playback_cd_forward_10)
                    )
                }

                // Next track
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.playback_cd_next)
                    )
                }

                // Repeat Button
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(RecovoDimensions.minTouchTarget)
                        .testTag("playback_repeat_button")
                ) {
                    when (playbackState.repeatMode) {
                        RepeatMode.OFF -> {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.playback_cd_repeat_off),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        RepeatMode.ALL -> {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.playback_cd_repeat_all),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        RepeatMode.ONE -> {
                            Icon(
                                imageVector = Icons.Default.RepeatOne,
                                contentDescription = stringResource(R.string.playback_cd_repeat_one),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Advanced Controls Row (Speed & Sleep Timer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Chip
                FilterChip(
                    selected = playbackState.playbackSpeed != 1.0f,
                    onClick = onOpenSpeedDialog,
                    label = { Text(stringResource(R.string.playback_speed_chip, playbackState.playbackSpeed.toString())) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("playback_speed_button")
                )

                // Sleep Timer Chip
                val timerLabel = playbackState.sleepTimerRemainingMs?.let { remainingMs ->
                    val mins = (remainingMs / 60000L).coerceAtLeast(1L)
                    stringResource(R.string.playback_timer_remaining, mins.toInt())
                } ?: stringResource(R.string.playback_timer_off)

                FilterChip(
                    selected = playbackState.sleepTimerRemainingMs != null,
                    onClick = onOpenTimerDialog,
                    label = { Text(timerLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("playback_sleep_timer_button")
                )
            }
        }
    }
}
