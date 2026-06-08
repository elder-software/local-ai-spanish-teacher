package com.eldersoftware.anytimespanish.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.eldersoftware.anytimespanish.di.AppContainer
import com.eldersoftware.anytimespanish.domain.model.ConversationTopic
import com.eldersoftware.anytimespanish.ui.chat.ChatViewModel
import com.eldersoftware.anytimespanish.ui.dashboard.DashboardViewModel
import com.eldersoftware.anytimespanish.ui.feedback.FeedbackViewModel
import com.eldersoftware.anytimespanish.ui.onboarding.OnboardingDownloadViewModel
import com.eldersoftware.anytimespanish.ui.onboarding.PaywallViewModel

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

class PaywallViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaywallViewModel::class.java)) {
            return PaywallViewModel(appContainer.purchaseRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
