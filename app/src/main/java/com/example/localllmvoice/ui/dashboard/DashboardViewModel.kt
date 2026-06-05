package com.example.localllmvoice.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.gemma.GemmaModelConfig
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.data.repository.ModelAvailability
import com.example.localllmvoice.data.repository.ModelDownloadEvent
import com.example.localllmvoice.di.AppContainer
import com.example.localllmvoice.domain.model.ConversationTopics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DashboardUiState(
            topics = ConversationTopics.all,
            modelStatusMessage = "Checking device and ${GemmaModelConfig.MODEL_LABEL} model…",
            practiceModeEnabled = appContainer.practiceModeEnabled,
        ),
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshModelStatus()
    }

    fun refreshModelStatus() {
        viewModelScope.launch {
            appContainer.llmRepository().checkModelAvailability().collect { availability ->
                applyAvailability(availability)
            }
        }
    }

    fun setPracticeMode(enabled: Boolean) {
        appContainer.enablePracticeMode(enabled)
        _uiState.update { it.copy(practiceModeEnabled = enabled, errorMessage = null) }
        refreshModelStatus()
    }

    fun downloadModel() {
        if (appContainer.practiceModeEnabled) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    modelStatus = GemmaModelStatus.DOWNLOADING,
                    errorMessage = null,
                )
            }
            appContainer.llmRepository().downloadModel().collect { event ->
                when (event) {
                    is ModelDownloadEvent.Progress -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = true,
                                modelStatus = GemmaModelStatus.DOWNLOADING,
                                downloadProgressBytes = event.downloadedBytes,
                                downloadTotalBytes = event.totalBytes,
                                modelStatusMessage = formatDownloadMessage(
                                    event.downloadedBytes,
                                    event.totalBytes,
                                ),
                            )
                        }
                    }

                    ModelDownloadEvent.Completed -> {
                        _uiState.update { it.copy(isDownloading = false) }
                        refreshModelStatus()
                    }

                    is ModelDownloadEvent.Failed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                modelStatus = GemmaModelStatus.ERROR,
                                errorMessage = event.message,
                                modelStatusMessage = "Download failed",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyAvailability(availability: ModelAvailability) {
        _uiState.update {
            it.copy(
                modelStatus = availability.status,
                modelStatusMessage = availability.message,
                activeBackend = availability.activeBackend,
                deviceCapability = availability.deviceCapability,
                practiceModeEnabled = appContainer.practiceModeEnabled,
                downloadProgressBytes = availability.downloadedBytes,
                downloadTotalBytes = availability.totalBytes,
                errorMessage = if (availability.status == GemmaModelStatus.ERROR) {
                    availability.message
                } else {
                    null
                },
            )
        }
    }

    private fun formatDownloadMessage(downloaded: Long, total: Long): String {
        val downloadedMb = downloaded / 1_000_000
        val totalMb = total / 1_000_000
        return "Downloading ${GemmaModelConfig.MODEL_LABEL}… $downloadedMb / $totalMb MB"
    }
}
