package com.example.localllmvoice.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.domain.CurrentDownload
import com.example.localllmvoice.domain.DownloadAllModelsEvent
import com.example.localllmvoice.domain.DownloadAllModelsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingDownloadUiState(
    val phase: Phase = Phase.Idle,
    val progressPercent: Int = 0,
    val currentDownload: CurrentDownload? = null,
    val errorMessage: String? = null,
) {
    enum class Phase { Idle, Downloading, Completed, Failed }
}

class OnboardingDownloadViewModel(
    private val downloadAllModelsUseCase: DownloadAllModelsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingDownloadUiState())
    val uiState: StateFlow<OnboardingDownloadUiState> = _uiState.asStateFlow()

    fun startDownload() {
        if (_uiState.value.phase == OnboardingDownloadUiState.Phase.Downloading) return

        _uiState.update {
            it.copy(
                phase = OnboardingDownloadUiState.Phase.Downloading,
                progressPercent = 0,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            downloadAllModelsUseCase().collect { event ->
                when (event) {
                    is DownloadAllModelsEvent.Progress -> {
                        _uiState.update {
                            it.copy(
                                phase = OnboardingDownloadUiState.Phase.Downloading,
                                progressPercent = event.progressPercent,
                                currentDownload = event.currentDownload,
                            )
                        }
                    }

                    DownloadAllModelsEvent.Completed -> {
                        _uiState.update {
                            it.copy(
                                phase = OnboardingDownloadUiState.Phase.Completed,
                                progressPercent = 100,
                            )
                        }
                    }

                    is DownloadAllModelsEvent.Failed -> {
                        _uiState.update {
                            it.copy(
                                phase = OnboardingDownloadUiState.Phase.Failed,
                                errorMessage = "Download failed",
                            )
                        }
                    }
                }
            }
        }
    }
}
