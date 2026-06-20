package com.eldersoftware.anytimespanish.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldersoftware.anytimespanish.ui.components.ChatMessageList
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

// Bottom of the meter's dynamic range in dBFS; 0 dB is full-scale PCM16.
private const val METER_FLOOR_DB = -60f

private const val MIC_PERMISSION_PREFS = "permissions"
private const val KEY_REQUESTED_RECORD_AUDIO = "requested_record_audio"

private fun hasRequestedRecordAudio(context: Context): Boolean =
    context.getSharedPreferences(MIC_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_REQUESTED_RECORD_AUDIO, false)

private fun markRecordAudioRequested(context: Context) {
    context.getSharedPreferences(MIC_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_REQUESTED_RECORD_AUDIO, true)
        .apply()
}

/**
 * True when the system will not show the permission sheet again and the user must use Settings.
 * [shouldShowRequestPermissionRationale] alone cannot distinguish "never asked" from "don't ask
 * again" (both return false); we need a persisted flag from a prior request attempt.
 */
private fun shouldOpenMicSettings(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val activity = context as? Activity
    if (activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    ) {
        return false
    }
    return hasRequestedRecordAudio(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onEndChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markRecordAudioRequested(context)
        if (granted) {
            viewModel.onMicrophonePermissionGranted()
        } else {
            viewModel.onMicrophonePermissionDenied(shouldOpenMicSettings(context))
        }
    }

    val openAppSettings = remember(context) {
        {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            context.startActivity(intent)
        }
    }

    when (val state = uiState) {
        ChatUiState.Initializing -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ChatUiState.ActiveConversation -> {
            if (state.showMicrophonePermissionDialog) {
                val needsSettings =
                    state.microphonePermissionNeedsSettings || shouldOpenMicSettings(context)
                MicrophonePermissionDialog(
                    needsSettings = needsSettings,
                    onDismiss = viewModel::dismissMicrophonePermissionDialog,
                    onAllowMicrophone = {
                        if (shouldOpenMicSettings(context)) {
                            openAppSettings()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onOpenSettings = openAppSettings,
                )
            }

            ActiveChatContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onEndChat = onEndChat,
                onToggleRecording = viewModel::toggleRecording,
                onCancelVoiceInput = viewModel::cancelVoiceInput,
                onDismissError = viewModel::dismissError,
                onToggleSuggestion = viewModel::toggleSuggestion,
                onTranslateClick = viewModel::translateMessage,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MicrophonePermissionDialog(
    needsSettings: Boolean,
    onDismiss: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Microphone access required") },
        text = {
            Text("To practise conversation, the app needs to permission hear you speak.")
        },
        confirmButton = {
            TextButton(
                onClick = if (needsSettings) onOpenSettings else onAllowMicrophone,
            ) {
                Text(if (needsSettings) "Open Settings" else "Allow microphone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveChatContent(
    state: ChatUiState.ActiveConversation,
    onNavigateBack: () -> Unit,
    onEndChat: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelVoiceInput: () -> Unit,
    onDismissError: () -> Unit,
    onToggleSuggestion: () -> Unit,
    onTranslateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(state.messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    // Follow the conversation as the streaming reply grows the content height, but only when
    // the user is already near the bottom so reading back through history is never hijacked.
    LaunchedEffect(scrollState.maxValue) {
        val followThresholdPx = with(density) { 160.dp.toPx() }
        val distanceFromBottom = scrollState.maxValue - scrollState.value
        if (distanceFromBottom in 1..followThresholdPx.toInt()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.currentTopic) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onEndChat) {
                        Text("End Chat")
                    }
                },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                ) {
                    if (state.messages.isEmpty()) {
                        Text(
                            text = "Tap the microphone to start speaking in Spanish.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ChatMessageList(
                            messages = state.messages,
                            onTranslateClick = onTranslateClick,
                            isTranslateEnabled = !state.isGenerating
                        )
                    }

                    // Only shown while waiting for the first token (the LLM prefill pause); once
                    // the assistant message starts streaming the bubble itself shows progress.
                    if (state.isGenerating && state.messages.lastOrNull()?.isUser == true) {
                        TypingIndicatorBubble(
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Stays visible through the transcribing phase so the words the user just
                    // spoke remain on screen while the final transcript settles, instead of
                    // vanishing into a blank pause.
                    AnimatedVisibility(
                        visible = state.isRecording || state.isTranscribing,
                        enter = slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(160)) { -it / 6 } + fadeOut(tween(160)),
                    ) {
                        MicDebugPanel(
                            inputLevel = state.inputLevel,
                            heardText = state.interimTranscript,
                            isFinalizing = state.isTranscribing,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    state.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        TextButton(onClick = onDismissError) {
                            Text("Dismiss")
                        }
                    }

                    RecordingControls(
                        isRecording = state.isRecording,
                        isTranscribing = state.isTranscribing,
                        enabled = !state.isGenerating && !state.isTranscribing,
                        suggestedReply = state.suggestedReply,
                        isSuggestionVisible = state.isSuggestionVisible,
                        isGeneratingSuggestion = state.isGeneratingSuggestion,
                        onToggleRecording = onToggleRecording,
                        onCancelVoiceInput = onCancelVoiceInput,
                        onToggleSuggestion = onToggleSuggestion,
                    )
                }
            }
        }
    }
}

@Composable
private fun MicDebugPanel(
    inputLevel: Float,
    heardText: String?,
    isFinalizing: Boolean,
    modifier: Modifier = Modifier,
) {
    // Speech sits low on a linear scale, so map RMS onto a dBFS range for a readable meter.
    val dbfs = if (inputLevel > 0f) 20f * log10(inputLevel) else METER_FLOOR_DB
    val displayLevel = ((dbfs - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
    val animatedLevel by animateFloatAsState(
        targetValue = displayLevel,
        animationSpec = tween(120),
        label = "mic_level",
    )
    val heard = heardText?.takeIf { it.isNotBlank() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isFinalizing) "Transcribing" else "Mic input",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isFinalizing) {
                    Text(
                        text = if (inputLevel > 0f) "${dbfs.roundToInt()} dB" else "-∞ dB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isFinalizing) {
                // The mic is stopped, so a level meter would just freeze; show activity instead.
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedLevel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            Text(
                text = heard ?: if (isFinalizing) "Finishing up…" else "Listening…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (heard != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Chat-style "partner is typing" placeholder shown during the LLM prefill pause, before the
 * first token arrives. Styled to match the assistant [ChatBubble] so the eventual message
 * appears to replace it in place.
 */
@Composable
private fun TypingIndicatorBubble(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing_indicator")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
        ),
        label = "typing_phase",
    )

    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                // A pulse travels across the dots: each dot brightens as the phase passes it.
                val offset = (phase - index + 3f) % 3f
                val alpha = if (offset < 1f) {
                    0.35f + 0.55f * (1f - abs(offset * 2f - 1f))
                } else {
                    0.35f
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestedReply: String?,
    isGeneratingSuggestion: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Try saying",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isGeneratingSuggestion) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Thinking of a suggestion...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = suggestedReply.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private enum class RecordingPhase { Idle, Recording, Transcribing }

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    isTranscribing: Boolean,
    enabled: Boolean,
    suggestedReply: String?,
    isSuggestionVisible: Boolean,
    isGeneratingSuggestion: Boolean,
    onToggleRecording: () -> Unit,
    onCancelVoiceInput: () -> Unit,
    onToggleSuggestion: () -> Unit,
) {
    val phase = when {
        isRecording -> RecordingPhase.Recording
        isTranscribing -> RecordingPhase.Transcribing
        else -> RecordingPhase.Idle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        AnimatedVisibility(
            visible = isSuggestionVisible,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 6 },
            exit = fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 6 },
        ) {
            SuggestionCard(
                suggestedReply = suggestedReply,
                isGeneratingSuggestion = isGeneratingSuggestion,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 } togetherWith
                    fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 4 }
            },
            label = "recording_controls",
        ) { currentPhase ->
            when (currentPhase) {
                RecordingPhase.Recording -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onCancelVoiceInput,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text("Cancel")
                        }
                        RecordingButton(
                            isRecording = true,
                            enabled = enabled,
                            onClick = onToggleRecording,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                RecordingPhase.Transcribing -> {
                    TranscribingButton()
                }

                RecordingPhase.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecordingButton(
                            isRecording = false,
                            enabled = enabled,
                            onClick = onToggleRecording,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(
                            onClick = onToggleSuggestion,
                            enabled = enabled,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = when {
                                    isGeneratingSuggestion -> "Cancel suggestion"
                                    isSuggestionVisible -> "Refresh suggestion"
                                    else -> "Show suggested reply"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscribingButton(
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = {},
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Transcribing…",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun RecordingButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseScale = if (isRecording) {
        val transition = rememberInfiniteTransition(label = "mic_pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic_scale",
        ).value
    } else {
        1f
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start recording",
            modifier = if (isRecording) Modifier.scale(pulseScale) else Modifier,
        )
        Text(
            text = if (isRecording) "Tap to send" else "Tap to talk",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
