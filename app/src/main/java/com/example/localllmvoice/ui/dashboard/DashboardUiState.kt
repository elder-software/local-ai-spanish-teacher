package com.example.localllmvoice.ui.dashboard

import com.example.localllmvoice.domain.model.ConversationTopic
import com.example.localllmvoice.domain.model.ConversationTopics

data class DashboardUiState(
    val topics: List<ConversationTopic> = ConversationTopics.all,
    val modelStatus: UiModelState? = UiModelState.Loading,
    val errorMessage: String? = null,
    val isCardVisible: Boolean = true,
) {
    val canStartConversation: Boolean = modelStatus == UiModelState.Ready || modelStatus == null

    sealed interface UiModelState {
        object Loading : UiModelState
        object Ready : UiModelState
        object Error : UiModelState
    }
}
