package com.example.localllmvoice.di

import android.content.Context
import com.example.localllmvoice.BuildConfig
import com.example.localllmvoice.data.audio.AndroidSpeechToTextManager
import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.audio.SpeechToTextManager
import com.example.localllmvoice.data.audio.TextToSpeechManager
import com.example.localllmvoice.data.audio.VoskSpeechToTextManager
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.domain.model.FeedbackSessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val speechToTextManager: SpeechToTextEngine = createSpeechToTextManager()
    val textToSpeechManager = TextToSpeechManager(appContext)
    val feedbackSessionStore = FeedbackSessionStore()
    val gemmaLlmRepository = GemmaLlmRepository(appContext)

    private fun createSpeechToTextManager(): SpeechToTextEngine =
        when (BuildConfig.SPEECH_TO_TEXT_ENGINE) {
            "android" -> AndroidSpeechToTextManager(appContext)
            "vosk" -> VoskSpeechToTextManager(appContext)
            "moonshine" -> SpeechToTextManager(appContext)
            else -> SpeechToTextManager(appContext)
        }
}
