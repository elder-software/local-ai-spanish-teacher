package com.eldersoftware.anytimespanish.ui.chat

import com.eldersoftware.anytimespanish.domain.model.ChatMessage

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
        val suggestedReply: String? = null,
        val isSuggestionVisible: Boolean = false,
        val isGeneratingSuggestion: Boolean = false,
        val showMicrophonePermissionDialog: Boolean = false,
        val microphonePermissionNeedsSettings: Boolean = false,
    ) : ChatUiState
}
