package com.eldersoftware.anytimespanish.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldersoftware.anytimespanish.di.AppContainer
import com.eldersoftware.anytimespanish.domain.model.CombinedModelStatus
import com.eldersoftware.anytimespanish.domain.model.ModelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var statusJob: Job? = null
    private var isFirstEmission = true

    init {
        refreshModelStatus()
        viewModelScope.launch {
            appContainer.purchaseRepository.isEntitled.collect { entitled ->
                _uiState.update { it.copy(isEntitled = entitled) }
            }
        }
    }

    fun refreshModelStatus() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            appContainer.observeModelStatusUseCase().collect { combinedStatus ->
                handleModelStatusUpdate(combinedStatus)
            }
        }
    }

    private fun handleModelStatusUpdate(combinedStatus: CombinedModelStatus) {
        val modelStatus = when (combinedStatus.status) {
            ModelStatus.READY -> DashboardUiState.UiModelState.Ready
            ModelStatus.INITIALIZING -> DashboardUiState.UiModelState.Loading
            else -> DashboardUiState.UiModelState.Error
        }

        val isReady = combinedStatus.status == ModelStatus.READY

        if (isReady) {
            if (isFirstEmission) {
                _uiState.update { currentState ->
                    currentState.copy(
                        modelStatus = null,
                        errorMessage = combinedStatus.errorMessage,
                        isCardVisible = false,
                    )
                }
                isFirstEmission = false
            } else {
                _uiState.update { currentState ->
                    currentState.copy(
                        modelStatus = modelStatus,
                        errorMessage = combinedStatus.errorMessage,
                        isCardVisible = true,
                    )
                }
                viewModelScope.launch {
                    delay(3000)
                    _uiState.update { currentState ->
                        currentState.copy(
                            modelStatus = null,
                            isCardVisible = false,
                        )
                    }
                }
            }
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    modelStatus = modelStatus,
                    errorMessage = combinedStatus.errorMessage,
                    isCardVisible = true,
                )
            }
            isFirstEmission = false
        }
    }
}
