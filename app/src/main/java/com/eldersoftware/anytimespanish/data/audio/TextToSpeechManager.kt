package com.eldersoftware.anytimespanish.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TextToSpeechManager(context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private val pendingUtterances = mutableListOf<String>()
    private val preferredLocales = listOf(
        Locale.forLanguageTag("es-ES"),
        Locale.forLanguageTag("es-MX"),
        Locale.forLanguageTag("es"),
    )

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = textToSpeech ?: return@TextToSpeech
                val locale = preferredLocales.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                    ?: Locale.getDefault()
                engine.language = locale
                engine.setSpeechRate(SPEECH_RATE)
                isReady.set(true)
                flushPendingUtterances(engine)
            }
        }
    }

    suspend fun speak(text: String) {
        if (text.isBlank() || !isReady.get()) return
        val engine = textToSpeech ?: return
        suspendCancellableCoroutine { continuation ->
            val utteranceId = "anytime_spanish_${System.currentTimeMillis()}"
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            continuation.invokeOnCancellation {
                engine.stop()
            }
        }
    }

    fun enqueue(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val engine = textToSpeech
        if (isReady.get() && engine != null) {
            enqueue(engine, trimmed)
        } else {
            synchronized(pendingUtterances) {
                pendingUtterances += trimmed
            }
        }
    }

    fun stop() {
        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }
        textToSpeech?.stop()
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady.set(false)
    }

    private fun flushPendingUtterances(engine: TextToSpeech) {
        val utterances = synchronized(pendingUtterances) {
            pendingUtterances.toList().also { pendingUtterances.clear() }
        }
        utterances.forEach { enqueue(engine, it) }
    }

    private fun enqueue(engine: TextToSpeech, text: String) {
        val utteranceId = "anytime_spanish_${System.nanoTime()}"
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private companion object {
        private const val SPEECH_RATE = 1.18f
    }
}
