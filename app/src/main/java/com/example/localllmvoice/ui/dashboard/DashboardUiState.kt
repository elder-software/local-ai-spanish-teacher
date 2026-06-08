package com.example.localllmvoice.ui.dashboard

import com.example.localllmvoice.domain.model.ConversationTopics
import com.example.localllmvoice.domain.model.TopicCategory

data class DashboardUiState(
    val categories: List<TopicCategory> = ConversationTopics.categories,
    val modelStatus: UiModelState? = UiModelState.Loading,
    val errorMessage: String? = null,
    val isCardVisible: Boolean = true,
    val isEntitled: Boolean = false,
) {
    val canStartConversation: Boolean = modelStatus == UiModelState.Ready || modelStatus == null

    fun isCategoryUnlocked(category: TopicCategory): Boolean = category.isFree || isEntitled

    sealed interface UiModelState {
        object Loading : UiModelState
        object Ready : UiModelState
        object Error : UiModelState
    }
}
