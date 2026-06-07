package com.example.localllmvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localllmvoice.domain.CurrentDownload

@Composable
fun OnboardingDownloadScreen(
    viewModel: OnboardingDownloadViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScaffold(
        heading = "Placing the AI on your device...",
        body = "To guarantee zero lag and total privacy, we are downloading your AI tutor's 'brain' directly to your phone (2.7GB, ~10 minutes). You will need Wi-Fi for this step. Once finished, you'll never need the internet to practice again.",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.phase == OnboardingDownloadUiState.Phase.Downloading ||
                uiState.phase == OnboardingDownloadUiState.Phase.Completed
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val progressLabel = when (uiState.currentDownload) {
                            CurrentDownload.Gemma -> "DOWNLOADING BRAIN..."
                            CurrentDownload.STT -> "DOWNLOADING EARS..."
                            null -> "DOWNLOADING..."
                        }
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${uiState.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    LinearProgressIndicator(
                        progress = { uiState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(percent = 50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi connection icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Strong connection required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            val buttonText = when (uiState.phase) {
                OnboardingDownloadUiState.Phase.Idle -> "Start download"
                OnboardingDownloadUiState.Phase.Downloading -> "Downloading..."
                OnboardingDownloadUiState.Phase.Completed -> "Start talking"
                OnboardingDownloadUiState.Phase.Failed -> "Retry download"
            }

            val buttonEnabled = uiState.phase != OnboardingDownloadUiState.Phase.Downloading

            val buttonOnClick = when (uiState.phase) {
                OnboardingDownloadUiState.Phase.Idle,
                OnboardingDownloadUiState.Phase.Failed -> viewModel::startDownload
                OnboardingDownloadUiState.Phase.Completed -> onFinished
                OnboardingDownloadUiState.Phase.Downloading -> ({})
            }

            OnboardingPrimaryButton(
                text = buttonText,
                onClick = buttonOnClick,
                enabled = buttonEnabled,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
