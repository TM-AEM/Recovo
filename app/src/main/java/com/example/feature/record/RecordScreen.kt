package com.example.feature.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.designsystem.theme.RecovoDimensions
import com.example.core.designsystem.theme.RecovoSpacing
import com.example.core.engine.RecordingState
import com.example.core.service.RecordingController
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { RecordingController(context.applicationContext) }
    val viewModel = remember { RecordViewModel(controller) }
    val state by viewModel.recordingState.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
        }
    }

    var customTitle by remember { mutableStateOf("") }

    // Permission handling
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showBackConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            showPermissionRationale = (context as? Activity)?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO
            ) != true
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Handled gracefully without blocking
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val isActivelyRecording = state is RecordingState.Recording || state is RecordingState.Paused
    BackHandler(enabled = isActivelyRecording) {
        showBackConfirmDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorder Studio") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isActivelyRecording) {
                                showBackConfirmDialog = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("record_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(RecovoSpacing.medium)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Permissions & Quality Preset
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Warning Card
                if (!hasMicPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = RecovoSpacing.medium)
                            .testTag("record_permission_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(RecovoSpacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Microphone Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(RecovoSpacing.small))
                            Text(
                                text = "Recovo requires microphone access to record audio locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(RecovoSpacing.medium))
                            Button(
                                onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }

                // Audio Interruption Notice Banner
                val isInterrupted = (state as? RecordingState.Paused)?.isInterrupted == true
                if (isInterrupted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = RecovoSpacing.medium)
                            .testTag("recording_interruption_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(RecovoSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(RecovoSpacing.small))
                            Column {
                                Text(
                                    text = stringResource(R.string.interruption_paused_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.interruption_paused_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                // Quality Card / Locked Badge
                RecordingQualityCard(
                    selectedQuality = selectedQuality,
                    isRecordingActive = isActivelyRecording,
                    onSelectQuality = { viewModel.setQuality(it) },
                    modifier = Modifier.padding(bottom = RecovoSpacing.small)
                )

                // Optional Pre-recording Custom Title
                if (!isActivelyRecording) {
                    Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text(stringResource(R.string.recording_title_optional)) },
                        placeholder = { Text(stringResource(R.string.recording_title_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_custom_title_input"),
                        trailingIcon = {
                            if (customTitle.isNotEmpty()) {
                                IconButton(onClick = { customTitle = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Title")
                                }
                            }
                        }
                    )
                }
            }

            // Center Section: Pulse Visualizer, Dynamic Waveform & High-Precision Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RecovoSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val elapsedMs = when (val s = state) {
                    is RecordingState.Recording -> s.elapsedMs
                    is RecordingState.Paused -> s.elapsedMs
                    else -> 0L
                }

                val currentAmp = when (val s = state) {
                    is RecordingState.Recording -> s.amplitude
                    else -> 0
                }

                // Studio Pulse Visualizer
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                is RecordingState.Recording -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                is RecordingState.Paused -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = when (state) {
                            is RecordingState.Recording -> MaterialTheme.colorScheme.error
                            is RecordingState.Paused -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(RecovoSpacing.medium))

                // High-Precision Formatted Timer
                Text(
                    text = formatTimer(elapsedMs),
                    fontSize = 42.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("recording_timer_text")
                )

                Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))

                // State Indicator Label
                val stateLabel = when (state) {
                    is RecordingState.Idle -> "READY TO RECORD"
                    is RecordingState.Preparing -> "PREPARING..."
                    is RecordingState.Recording -> "RECORDING"
                    is RecordingState.Paused -> if ((state as RecordingState.Paused).isInterrupted) "PAUSED (INTERRUPTED)" else "PAUSED"
                    is RecordingState.Stopping -> "SAVING..."
                    is RecordingState.Saved -> "SAVED SUCCESSFULLY"
                    is RecordingState.Error -> "ERROR OCCURRED"
                }

                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (state) {
                        is RecordingState.Recording -> MaterialTheme.colorScheme.error
                        is RecordingState.Paused -> MaterialTheme.colorScheme.tertiary
                        is RecordingState.Saved -> MaterialTheme.colorScheme.primary
                        is RecordingState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(RecovoSpacing.small))

                // Dynamic Audio Waveform Equalizer
                DynamicWaveformVisualizer(
                    amplitude = currentAmp,
                    isRecording = state is RecordingState.Recording,
                    isPaused = state is RecordingState.Paused,
                    modifier = Modifier.padding(vertical = RecovoSpacing.small)
                )

                // Real-time Estimated File Size Badge
                AnimatedVisibility(
                    visible = isActivelyRecording,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    EstimatedFileSizeBadge(
                        bitRate = selectedQuality.bitRate,
                        elapsedMs = elapsedMs,
                        modifier = Modifier.padding(top = RecovoSpacing.small)
                    )
                }

                // Error Message if any
                if (state is RecordingState.Error) {
                    Spacer(modifier = Modifier.height(RecovoSpacing.medium))
                    Text(
                        text = (state as RecordingState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Bottom Section: Recording Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = RecovoSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is RecordingState.Idle, is RecordingState.Saved, is RecordingState.Error -> {
                        Button(
                            onClick = {
                                if (hasMicPermission) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    viewModel.startRecording(customTitle.trim().ifEmpty { null })
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RecovoDimensions.minTouchTarget)
                                .testTag("record_start_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.padding(end = RecovoSpacing.small)
                            )
                            Text(text = "Start Recording")
                        }
                    }

                    is RecordingState.Recording -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button (prompts discard dialog)
                            OutlinedButton(
                                onClick = { showDiscardConfirmDialog = true },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_cancel_button")
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Cancel")
                            }

                            // Pause button
                            FilledTonalButton(
                                onClick = { viewModel.pauseRecording(isInterrupted = false) },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_pause_button")
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Pause")
                            }

                            // Stop & Save button
                            Button(
                                onClick = { viewModel.stopRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_stop_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Save")
                            }
                        }
                    }

                    is RecordingState.Paused -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button (prompts discard dialog)
                            OutlinedButton(
                                onClick = { showDiscardConfirmDialog = true },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_cancel_button")
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Cancel")
                            }

                            // Resume button
                            Button(
                                onClick = { viewModel.resumeRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_resume_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Resume")
                            }

                            // Stop & Save button
                            FilledTonalButton(
                                onClick = { viewModel.stopRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_stop_button")
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text("Save")
                            }
                        }
                    }

                    is RecordingState.Preparing, is RecordingState.Stopping -> {
                        Text(
                            text = "Please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Discard Recording Confirmation Dialog
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text(stringResource(R.string.recording_discard_title)) },
            text = { Text(stringResource(R.string.recording_discard_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        viewModel.cancelRecording()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("record_discard_confirm_button")
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardConfirmDialog = false },
                    modifier = Modifier.testTag("record_discard_cancel_button")
                ) {
                    Text(stringResource(R.string.keep_recording))
                }
            }
        )
    }

    // Permission Rationale / Settings Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Permission Settings") },
            text = { Text("Microphone permission was permanently denied or disabled. Please enable it in App Settings to allow recording.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        openAppSettings(context)
                    }
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Back Navigation Confirmation Dialog
    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("Recording in Progress") },
            text = { Text("A recording is actively running in the background. Would you like to keep recording or stop and save?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBackConfirmDialog = false
                        viewModel.stopRecording()
                        onBack()
                    }
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showBackConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("Continue in Background")
                }
            }
        )
    }
}

private fun formatTimer(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (elapsedMs % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
