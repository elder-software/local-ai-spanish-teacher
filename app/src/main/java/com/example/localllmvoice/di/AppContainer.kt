package com.example.localllmvoice.di

import android.content.Context
import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.audio.TextToSpeechManager
import com.example.localllmvoice.data.audio.VoskSpeechToTextManager
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.domain.DownloadAllModelsUseCase
import com.example.localllmvoice.domain.model.FeedbackSessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val speechToTextManager: SpeechToTextEngine = VoskSpeechToTextManager(appContext)
    val textToSpeechManager = TextToSpeechManager(appContext)
    val feedbackSessionStore = FeedbackSessionStore()
    val gemmaLlmRepository = GemmaLlmRepository(appContext)
    val downloadAllModelsUseCase = DownloadAllModelsUseCase(gemmaLlmRepository, speechToTextManager)
}
