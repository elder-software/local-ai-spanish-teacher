package com.example.localllmvoice.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.gemma.GemmaModelConfig
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.data.repository.ModelAvailability
import com.example.localllmvoice.di.AppContainer
import com.example.localllmvoice.domain.DownloadAllModelsEvent
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
        ),
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshModelStatus()
    }

    fun refreshModelStatus() {
        viewModelScope.launch {
            appContainer.gemmaLlmRepository.checkModelAvailability().collect { availability ->
                val sttReady = appContainer.speechToTextManager.isModelReady()
                if (!sttReady && availability.status == GemmaModelStatus.READY) {
                    _uiState.update {
                        it.copy(
                            modelStatus = GemmaModelStatus.DOWNLOAD_REQUIRED,
                            modelStatusMessage = "Download Spanish STT model to run offline",
                            activeBackend = availability.activeBackend,
                            deviceCapability = availability.deviceCapability,
                            downloadProgressBytes = 0L,
                            downloadTotalBytes = 0L,
                            errorMessage = null,
                        )
                    }
                } else if (!sttReady && availability.status == GemmaModelStatus.DOWNLOAD_REQUIRED) {
                    _uiState.update {
                        it.copy(
                            modelStatus = GemmaModelStatus.DOWNLOAD_REQUIRED,
                            modelStatusMessage = "Download Gemma and Spanish STT models (~${GemmaModelConfig.ESTIMATED_SIZE_MB} MB) to run offline",
                            activeBackend = availability.activeBackend,
                            deviceCapability = availability.deviceCapability,
                            downloadProgressBytes = 0L,
                            downloadTotalBytes = 0L,
                            errorMessage = null,
                        )
                    }
                } else {
                    applyAvailability(availability)
                }
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    modelStatus = GemmaModelStatus.DOWNLOADING,
                    errorMessage = null,
                )
            }
            appContainer.downloadAllModelsUseCase().collect { event ->
                when (event) {
                    is DownloadAllModelsEvent.GemmaProgress -> {
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

                    is DownloadAllModelsEvent.SttProgress -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = true,
                                modelStatus = GemmaModelStatus.DOWNLOADING,
                                downloadProgressBytes = event.progressPercent.toLong(),
                                downloadTotalBytes = 100L,
                                modelStatusMessage = "Downloading Spanish STT model… ${event.progressPercent}%",
                            )
                        }
                    }

                    DownloadAllModelsEvent.Completed -> {
                        _uiState.update { it.copy(isDownloading = false) }
                        refreshModelStatus()
                    }

                    is DownloadAllModelsEvent.Failed -> {
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
