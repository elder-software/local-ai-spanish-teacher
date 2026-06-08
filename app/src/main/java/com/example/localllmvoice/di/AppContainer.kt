package com.example.localllmvoice.di

import android.content.Context
import com.example.localllmvoice.data.audio.MoonshineSpeechToTextManager
import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.audio.TextToSpeechManager
import com.example.localllmvoice.data.onboarding.OnboardingPreferences
import com.example.localllmvoice.data.purchase.RevenueCatRepository
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.domain.DecideStartupStateUseCase
import com.example.localllmvoice.domain.DownloadAllModelsUseCase
import com.example.localllmvoice.domain.ObserveModelStatusUseCase
import com.example.localllmvoice.domain.model.FeedbackSessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

//    val speechToTextManager: SpeechToTextEngine = VoskSpeechToTextManager(appContext)
//    val speechToTextManager: SpeechToTextEngine = WhisperSpeechToTextManager(appContext)
    val speechToTextManager: SpeechToTextEngine = MoonshineSpeechToTextManager(appContext)
    val textToSpeechManager = TextToSpeechManager(appContext)
    val feedbackSessionStore = FeedbackSessionStore()
    val gemmaLlmRepository = GemmaLlmRepository(appContext)
    val downloadAllModelsUseCase = DownloadAllModelsUseCase(gemmaLlmRepository, speechToTextManager)
    val onboardingPreferences = OnboardingPreferences(appContext)
    val observeModelStatusUseCase = ObserveModelStatusUseCase(
        gemmaLlmRepository = gemmaLlmRepository,
        speechToTextEngine = speechToTextManager,
    )
    val decideStartupStateUseCase = DecideStartupStateUseCase(
        onboardingPreferences = onboardingPreferences,
        gemmaLlmRepository = gemmaLlmRepository,
        speechToTextEngine = speechToTextManager,
    )
    val purchaseRepository = RevenueCatRepository()

    init {
        speechToTextManager.preload()
        purchaseRepository.start()
    }
}
