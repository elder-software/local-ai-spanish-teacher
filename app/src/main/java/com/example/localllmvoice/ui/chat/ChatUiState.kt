package com.example.localllmvoice.ui.chat

import com.example.localllmvoice.domain.model.ChatMessage

sealed interface ChatUiState {
    data object Initializing : ChatUiState

    data class ActiveConversation(
        val messages: List<ChatMessage> = emptyList(),
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val isGenerating: Boolean = false,
        val currentTopic: String,
        val interimTranscript: String? = null,
        val errorMessage: String? = null,
        val inputLevel: Float = 0f,
    ) : ChatUiState
}
