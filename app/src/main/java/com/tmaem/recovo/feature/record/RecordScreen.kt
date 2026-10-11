package com.tmaem.recovo.feature.record

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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmaem.recovo.R
import com.tmaem.recovo.core.designsystem.components.RecovoStatusCard
import com.tmaem.recovo.core.designsystem.theme.RecovoDimensions
import com.tmaem.recovo.core.designsystem.theme.RecovoSpacing
import com.tmaem.recovo.core.engine.RecordingState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val state by viewModel.recordingState.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()

    var customTitle by rememberSaveable { mutableStateOf("") }

    // Microphone permission check
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var showBackConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            showPermissionRationale = (context as? Activity)?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO
            ) != true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val isActivelyRecording = state is RecordingState.Recording || state is RecordingState.Paused
    val isPreparingOrStopping = state is RecordingState.Preparing || state is RecordingState.Stopping

    // Intercept back gesture during active recording to prevent accidental loss
    BackHandler(enabled = isActivelyRecording) {
        showBackConfirmDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
                            contentDescription = stringResource(R.string.cancel)
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
            // Top Section: Permissions, Interruption Banner & Quality Preset
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Warning Card
                if (!hasMicPermission) {
                    RecovoStatusCard(
                        title = stringResource(R.string.record_permission_title),
                        body = stringResource(R.string.record_permission_message),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = RecovoSpacing.medium)
                            .testTag("record_permission_card"),
                        titleToBodySpacing = RecovoSpacing.small,
                        bodyToActionSpacing = RecovoSpacing.medium,
                        action = {
                            Button(
                                onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.record_permission_grant))
                            }
                        },
                    )
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
                                imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(RecovoSpacing.small))
                            Column {
                                Text(
                                    text = stringResource(R.string.interruption_paused_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
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
                    isRecordingActive = isActivelyRecording || isPreparingOrStopping,
                    onSelectQuality = { viewModel.setQuality(it) },
                    modifier = Modifier.padding(bottom = RecovoSpacing.small)
                )

                // Optional Pre-recording Custom Title (Editable only when Idle)
                if (!isActivelyRecording && !isPreparingOrStopping) {
                    Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it.take(80) },
                        label = { Text(stringResource(R.string.recording_title_optional)) },
                        placeholder = { Text(stringResource(R.string.recording_title_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_custom_title_input"),
                        trailingIcon = {
                            if (customTitle.isNotEmpty()) {
                                IconButton(onClick = { customTitle = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.record_clear_title))
                                }
                            }
                        }
                    )
                }
            }

            // Center Section: Studio Visualizer, Timer & State Presentation
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

                // Studio Status Icon / Hub
                StudioStatusHub(state = state)

                Spacer(modifier = Modifier.height(RecovoSpacing.medium))

                // High-Precision Formatted Monospace Timer
                Text(
                    text = formatTimer(elapsedMs),
                    fontSize = 42.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("recording_timer_text")
                )

                Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))

                // Professional Recording State Badge
                RecordingStateBadge(state = state)

                Spacer(modifier = Modifier.height(RecovoSpacing.small))

                // Lightweight Level Meter (Zero per-frame allocations)
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

                // Saved State Success Card
                if (state is RecordingState.Saved) {
                    val savedState = state as RecordingState.Saved
                    Spacer(modifier = Modifier.height(RecovoSpacing.medium))
                    RecovoStatusCard(
                        title = stringResource(R.string.recording_saved_title),
                        body = savedState.file.name,
                        icon = Icons.Default.CheckCircle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Error State Feedback
                if (state is RecordingState.Error) {
                    val errorState = state as RecordingState.Error
                    Spacer(modifier = Modifier.height(RecovoSpacing.medium))
                    RecovoStatusCard(
                        title = stringResource(R.string.state_error),
                        body = errorState.message,
                        icon = Icons.Default.ErrorOutline,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                        action = {
                            OutlinedButton(
                                onClick = { viewModel.resetState() }
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.record_dismiss))
                            }
                        },
                    )
                }
            }

            // Bottom Section: Professional Recording Controls
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
                                    val titleToUse = customTitle.trim().ifEmpty { null }
                                    viewModel.startRecording(titleToUse)
                                    customTitle = ""
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isPreparingOrStopping,
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
                            Text(
                                text = if (state is RecordingState.Saved) stringResource(R.string.record_another) else stringResource(R.string.start_recording),
                                fontWeight = FontWeight.Bold
                            )
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
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.cancel_recording))
                            }

                            // Pause button
                            FilledTonalButton(
                                onClick = { viewModel.pauseRecording(isInterrupted = false) },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_pause_button")
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = stringResource(R.string.pause_recording))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.pause_recording))
                            }

                            // Stop & Save button
                            Button(
                                onClick = { viewModel.stopRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_stop_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = stringResource(R.string.save))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.stop_and_save))
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
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.cancel_recording))
                            }

                            // Resume button
                            Button(
                                onClick = { viewModel.resumeRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_resume_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = stringResource(R.string.resume_recording))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.resume_recording))
                            }

                            // Stop & Save button
                            FilledTonalButton(
                                onClick = { viewModel.stopRecording() },
                                modifier = Modifier
                                    .height(RecovoDimensions.minTouchTarget)
                                    .testTag("record_stop_button")
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = stringResource(R.string.save))
                                Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                                Text(stringResource(R.string.stop_and_save))
                            }
                        }
                    }

                    is RecordingState.Preparing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(RecovoDimensions.minTouchTarget)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(RecovoSpacing.small))
                            Text(
                                text = stringResource(R.string.state_preparing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is RecordingState.Stopping -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(RecovoDimensions.minTouchTarget)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(RecovoSpacing.small))
                            Text(
                                text = stringResource(R.string.state_stopping),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

    // Permission Settings Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.record_permission_settings_title)) },
            text = { Text(stringResource(R.string.record_permission_settings_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        openAppSettings(context)
                    }
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(RecovoSpacing.extraSmall))
                    Text(stringResource(R.string.record_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Back Navigation Confirmation Dialog
    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text(stringResource(R.string.back_dialog_title)) },
            text = { Text(stringResource(R.string.back_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBackConfirmDialog = false
                        viewModel.stopRecording()
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.back_dialog_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { showBackConfirmDialog = false }
                    ) {
                        Text(stringResource(R.string.back_dialog_dismiss))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            showBackConfirmDialog = false
                            onBack()
                        }
                    ) {
                        Text(stringResource(R.string.back_dialog_continue))
                    }
                }
            }
        )
    }
}

/**
 * Center status indicator hub displaying distinct iconography and container styling per recording state.
 */
@Composable
private fun StudioStatusHub(
    state: RecordingState,
    modifier: Modifier = Modifier
) {
    val preparingCd = stringResource(R.string.a11y_preparing_microphone)
    val stoppingCd = stringResource(R.string.state_stopping)
    // Semantic state surfaces use real M3 container roles (no ad-hoc alpha overlays):
    // recording -> errorContainer (red, distinct from the amber brand),
    // paused/interrupted -> tertiaryContainer (slate, distinct from both),
    // preparing/stopping -> primaryContainer (amber), saved -> primaryContainer,
    // error -> errorContainer, idle -> surfaceVariant.
    val containerColor = when (state) {
        is RecordingState.Recording -> MaterialTheme.colorScheme.errorContainer
        is RecordingState.Paused -> MaterialTheme.colorScheme.tertiaryContainer
        is RecordingState.Preparing, is RecordingState.Stopping -> MaterialTheme.colorScheme.primaryContainer
        is RecordingState.Saved -> MaterialTheme.colorScheme.primaryContainer
        is RecordingState.Error -> MaterialTheme.colorScheme.errorContainer
        is RecordingState.Idle -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconColor = when (state) {
        is RecordingState.Recording -> MaterialTheme.colorScheme.onErrorContainer
        is RecordingState.Paused -> MaterialTheme.colorScheme.onTertiaryContainer
        is RecordingState.Preparing, is RecordingState.Stopping -> MaterialTheme.colorScheme.onPrimaryContainer
        is RecordingState.Saved -> MaterialTheme.colorScheme.onPrimaryContainer
        is RecordingState.Error -> MaterialTheme.colorScheme.onErrorContainer
        is RecordingState.Idle -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is RecordingState.Preparing -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = preparingCd },
                    color = iconColor,
                    strokeWidth = 3.dp
                )
            }
            is RecordingState.Stopping -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = stoppingCd },
                    color = iconColor,
                    strokeWidth = 3.dp
                )
            }
            is RecordingState.Paused -> {
                if (state.isInterrupted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                        contentDescription = stringResource(R.string.record_cd_interrupted),
                        tint = iconColor,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.record_cd_paused),
                        tint = iconColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            is RecordingState.Saved -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.record_cd_saved),
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
            is RecordingState.Error -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = stringResource(R.string.record_cd_error),
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.record_cd_microphone),
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

/** Four-tuple keeping label, icon and both colors aligned in [RecordingStateBadge]. */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Text badge communicating the exact state using icon + text so state is never conveyed by color alone.
 */
@Composable
private fun RecordingStateBadge(
    state: RecordingState,
    modifier: Modifier = Modifier
) {
    // Badge carries icon + text, so state is never color-only. Each entry pairs a
    // foreground "on*" role with its semantic container so contrast holds in both themes.
    val (label, icon, fgColor, bgColor) = when (state) {
        is RecordingState.Idle -> Quad(
            stringResource(R.string.state_idle),
            Icons.Default.Mic,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        is RecordingState.Preparing -> Quad(
            stringResource(R.string.state_preparing),
            Icons.Default.HourglassTop,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        is RecordingState.Recording -> Quad(
            stringResource(R.string.state_recording),
            Icons.Default.Mic,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
        is RecordingState.Paused -> if (state.isInterrupted) {
            Quad(
                stringResource(R.string.state_interrupted),
                Icons.Default.Warning,
                MaterialTheme.colorScheme.onTertiaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            )
        } else {
            Quad(
                stringResource(R.string.state_paused),
                Icons.Default.Pause,
                MaterialTheme.colorScheme.onTertiaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            )
        }
        is RecordingState.Stopping -> Quad(
            stringResource(R.string.state_stopping),
            Icons.Default.HourglassTop,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        is RecordingState.Saved -> Quad(
            stringResource(R.string.state_saved),
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        is RecordingState.Error -> Quad(
            stringResource(R.string.state_error),
            Icons.Default.ErrorOutline,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RecovoSpacing.small, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

/**
 * High-precision timer formatter:
 * "00:00" under 1 hour.
 * "01:05:30" 1 hour and above.
 */
fun formatTimer(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
