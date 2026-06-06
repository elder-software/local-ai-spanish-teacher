package com.example.localllmvoice.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localllmvoice.ui.components.ChatMessageList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onEndChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            ActiveChatContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onEndChat = onEndChat,
                onToggleRecording = viewModel::toggleRecording,
                onCancelVoiceInput = viewModel::cancelVoiceInput,
                onDismissError = viewModel::dismissError,
                modifier = modifier,
            )
        }
    }
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
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
                        ChatMessageList(messages = state.messages)
                    }

                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AnimatedVisibility(
                        visible = state.isRecording,
                        enter = slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(160)) { -it / 6 } + fadeOut(tween(160)),
                    ) {
                        val interim = state.interimTranscript?.takeIf { it.isNotBlank() }
                        Text(
                            text = interim ?: "Listening…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        enabled = !state.isGenerating,
                        onToggleRecording = onToggleRecording,
                        onCancelVoiceInput = onCancelVoiceInput,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    enabled: Boolean,
    onToggleRecording: () -> Unit,
    onCancelVoiceInput: () -> Unit,
) {
    AnimatedContent(
        targetState = isRecording,
        transitionSpec = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 } togetherWith
                fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 4 }
        },
        label = "recording_controls",
    ) { recording ->
        if (recording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
        } else {
            RecordingButton(
                isRecording = false,
                enabled = enabled,
                onClick = onToggleRecording,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
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
            .fillMaxWidth()
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
