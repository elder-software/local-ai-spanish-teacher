package com.eldersoftware.anytimespanish.ui.feedback

sealed interface FeedbackUiState {
    data object Empty : FeedbackUiState

    data class Analyzing(
        val topicTitle: String,
        val partialReport: String = "",
    ) : FeedbackUiState

    data class Success(
        val topicTitle: String,
        val report: String,
    ) : FeedbackUiState

    data class Error(
        val message: String,
    ) : FeedbackUiState
}
