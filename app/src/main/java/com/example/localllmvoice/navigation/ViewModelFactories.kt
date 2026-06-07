package com.example.localllmvoice.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.localllmvoice.di.AppContainer
import com.example.localllmvoice.domain.model.ConversationTopic
import com.example.localllmvoice.ui.chat.ChatViewModel
import com.example.localllmvoice.ui.dashboard.DashboardViewModel
import com.example.localllmvoice.ui.feedback.FeedbackViewModel
import com.example.localllmvoice.ui.onboarding.OnboardingDownloadViewModel

class DashboardViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(appContainer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModelFactory(
    private val topic: ConversationTopic,
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                topic = topic,
                llmRepository = appContainer.gemmaLlmRepository,
                speechToTextManager = appContainer.speechToTextManager,
                textToSpeechManager = appContainer.textToSpeechManager,
                feedbackSessionStore = appContainer.feedbackSessionStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class FeedbackViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedbackViewModel::class.java)) {
            return FeedbackViewModel(
                llmRepository = appContainer.gemmaLlmRepository,
                feedbackSessionStore = appContainer.feedbackSessionStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OnboardingDownloadViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingDownloadViewModel::class.java)) {
            return OnboardingDownloadViewModel(appContainer.downloadAllModelsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
