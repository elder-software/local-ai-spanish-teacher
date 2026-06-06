package com.example.localllmvoice.di

import android.content.Context
import com.example.localllmvoice.BuildConfig
import com.example.localllmvoice.data.audio.AndroidSpeechToTextManager
import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.audio.SpeechToTextManager
import com.example.localllmvoice.data.audio.TextToSpeechManager
import com.example.localllmvoice.data.repository.DemoLlmRepository
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.data.repository.LlmRepository
import com.example.localllmvoice.domain.model.FeedbackSessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val gemmaLlmRepository = GemmaLlmRepository(appContext)
    private val demoLlmRepository = DemoLlmRepository()

    var practiceModeEnabled: Boolean = false
        private set

    val speechToTextManager: SpeechToTextEngine = createSpeechToTextManager()
    val textToSpeechManager = TextToSpeechManager(appContext)
    val feedbackSessionStore = FeedbackSessionStore()

    fun llmRepository(): LlmRepository =
        if (practiceModeEnabled) demoLlmRepository else gemmaLlmRepository

    fun enablePracticeMode(enabled: Boolean) {
        practiceModeEnabled = enabled
    }

    private fun createSpeechToTextManager(): SpeechToTextEngine =
        when (BuildConfig.SPEECH_TO_TEXT_ENGINE) {
            "android" -> AndroidSpeechToTextManager(appContext)
            "moonshine" -> SpeechToTextManager(appContext)
            else -> SpeechToTextManager(appContext)
        }
}
