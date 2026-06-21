package com.eldersoftware.anytimespanish.di

import android.content.Context
import com.eldersoftware.anytimespanish.data.audio.SherpaOnnxSpeechToTextManager
import com.eldersoftware.anytimespanish.data.audio.SpeechToTextEngine
import com.eldersoftware.anytimespanish.data.audio.TextToSpeechManager
import com.eldersoftware.anytimespanish.data.onboarding.OnboardingPreferences
import com.eldersoftware.anytimespanish.data.purchase.RevenueCatRepository
import com.eldersoftware.anytimespanish.data.repository.GemmaLlmRepository
import com.eldersoftware.anytimespanish.domain.DecideStartupStateUseCase
import com.eldersoftware.anytimespanish.domain.DownloadAllModelsUseCase
import com.eldersoftware.anytimespanish.domain.ObserveModelStatusUseCase
import com.eldersoftware.anytimespanish.domain.model.FeedbackSessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

//    val speechToTextManager: SpeechToTextEngine = VoskSpeechToTextManager(appContext)
//    val speechToTextManager: SpeechToTextEngine = WhisperSpeechToTextManager(appContext)
//    val speechToTextManager: SpeechToTextEngine = MoonshineSpeechToTextManager(appContext)
    val speechToTextManager: SpeechToTextEngine = SherpaOnnxSpeechToTextManager(appContext)
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
