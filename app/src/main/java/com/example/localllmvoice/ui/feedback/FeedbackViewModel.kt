package com.example.localllmvoice.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.repository.LlmRepository
import com.example.localllmvoice.domain.model.FeedbackSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FeedbackViewModel(
    private val llmRepository: LlmRepository,
    private val feedbackSessionStore: FeedbackSessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Empty)
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        val session = feedbackSessionStore.consume()
        if (session == null) {
            _uiState.value = FeedbackUiState.Empty
        } else {
            _uiState.value = FeedbackUiState.Analyzing(topicTitle = session.topicTitle)
            analysisJob = viewModelScope.launch {
                val reportBuilder = StringBuilder()
                try {
                    llmRepository.analyzeConversation(session.transcript).collect { delta ->
                        reportBuilder.append(delta)
                        _uiState.value = FeedbackUiState.Analyzing(
                            topicTitle = session.topicTitle,
                            partialReport = reportBuilder.toString(),
                        )
                    }

                    _uiState.value = FeedbackUiState.Success(
                        topicTitle = session.topicTitle,
                        report = reportBuilder.toString().trim(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = FeedbackUiState.Error(
                        message = e.message ?: "No se pudo generar el informe",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        runBlocking {
            analysisJob?.cancelAndJoin()
            llmRepository.resetConversation()
        }
        super.onCleared()
    }
}
