package com.example.localllmvoice.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localllmvoice.data.gemma.DeviceCapability
import com.example.localllmvoice.data.gemma.GemmaModelConfig
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.domain.model.ConversationTopic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTopicSelected: (ConversationTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("SoloTalk AI") },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "status") {
                    ModelStatusCard(
                        message = uiState.modelStatusMessage,
                        backend = uiState.activeBackend,
                        capability = uiState.deviceCapability,
                        isDownloading = uiState.isDownloading,
                        downloadProgress = uiState.downloadProgressBytes,
                        downloadTotal = uiState.downloadTotalBytes,
                        practiceModeEnabled = uiState.practiceModeEnabled,
                        canDownload = uiState.canDownload,
                        showPracticeModeOffer = !uiState.practiceModeEnabled &&
                            (uiState.modelStatus == GemmaModelStatus.INSUFFICIENT_DEVICE ||
                                uiState.modelStatus == GemmaModelStatus.ERROR),
                        onDownload = viewModel::downloadModel,
                        onRefresh = viewModel::refreshModelStatus,
                        onEnablePracticeMode = { viewModel.setPracticeMode(true) },
                        onDisablePracticeMode = { viewModel.setPracticeMode(false) },
                    )
                }

                uiState.errorMessage?.let { error ->
                    item(key = "error") {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                item(key = "topics_header") {
                    Text(
                        text = "Choose a conversation topic",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(uiState.topics, key = { it.id }) { topic ->
                    TopicCard(
                        topic = topic,
                        enabled = uiState.canStartConversation,
                        onClick = { onTopicSelected(topic) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    message: String,
    backend: String?,
    capability: DeviceCapability?,
    isDownloading: Boolean,
    downloadProgress: Long,
    downloadTotal: Long,
    practiceModeEnabled: Boolean,
    canDownload: Boolean,
    showPracticeModeOffer: Boolean,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onEnablePracticeMode: () -> Unit,
    onDisablePracticeMode: () -> Unit,
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (practiceModeEnabled) "Practice mode" else "${GemmaModelConfig.MODEL_LABEL} (on-device)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            backend?.let {
                Text(
                    text = "Runtime: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            capability?.let { info ->
                Text(
                    text = info.deviceSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "RAM ${info.totalRamBytes / 1_000_000_000} GB · Storage free ${info.freeStorageBytes / 1_000_000_000} GB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.hints.forEach { hint ->
                    Text(
                        text = "• $hint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isDownloading && downloadTotal > 0) {
                LinearProgressIndicator(
                    progress = { downloadProgress.toFloat() / downloadTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            if (practiceModeEnabled) {
                OutlinedButton(onClick = onDisablePracticeMode) {
                    Text("Turn off practice mode")
                }
            } else {
                if (canDownload) {
                    Button(onClick = onDownload, enabled = !isDownloading) {
                        Text("Download ${GemmaModelConfig.MODEL_LABEL}")
                    }
                }
                Button(onClick = onRefresh, enabled = !isDownloading) {
                    Text("Refresh status")
                }
                if (showPracticeModeOffer) {
                    OutlinedButton(onClick = onEnablePracticeMode) {
                        Text("Use practice mode instead")
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(
    topic: ConversationTopic,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = topic.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
