package com.example.localllmvoice.data.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Speech-to-text backed by Android's platform [SpeechRecognizer].
 *
 * When the device has the on-device language pack installed we recognize through the dedicated
 * on-device recognizer (reliable, offline, no server round trips). Otherwise we use the default
 * recognizer (typically the online Google service) and try to download the on-device pack so
 * later sessions can run offline. The two recognizers use *different* model stores, so forcing the
 * default recognizer offline via [RecognizerIntent.EXTRA_PREFER_OFFLINE] is unreliable and avoided.
 */
class AndroidSpeechToTextManager(context: Context) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopRequested: (() -> Unit)? = null

    // Set once the on-device recognizer reports the language pack is installed.
    @Volatile
    private var onDeviceLanguageInstalled = false

    // Guards against issuing overlapping support checks / download requests.
    private val supportCheckInFlight = AtomicBoolean(false)

    init {
        // Detect (and, if missing, request) the on-device pack up front so the first tap is reliable.
        ensureOnDeviceSupport(DEFAULT_LANGUAGE, triggerDownload = true)
    }

    override fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun transcribe(languageTag: String): Flow<SttEvent> = callbackFlow {
        if (!hasRecordPermission()) {
            trySend(SttEvent.Failure("Microphone permission not granted"))
            close()
            return@callbackFlow
        }

        if (!isAvailable()) {
            trySend(SttEvent.Failure("Android speech recognition is unavailable on this device"))
            close()
            return@callbackFlow
        }

        val recognizerRef = AtomicReference<SpeechRecognizer?>()
        val completed = AtomicBoolean(false)
        val retryUsed = AtomicBoolean(false)
        val onDeviceMode = AtomicBoolean(canUseOnDevice())

        fun complete(event: SttEvent) {
            if (completed.compareAndSet(false, true)) {
                trySend(event)
                close()
            }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SttEvent.Partial("Listening…"))
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(SttEvent.Partial("Transcribing…"))
            }

            override fun onError(error: Int) {
                Log.w(TAG, "Android speech recognition error: $error")
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    complete(SttEvent.Final(""))
                    return
                }

                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                ) {
                    // Pack not where this recognizer looked; (re)check and fetch for next time.
                    onDeviceLanguageInstalled = false
                    ensureOnDeviceSupport(languageTag, triggerDownload = true)
                }

                // Retry once on the other recognizer: on-device <-> online.
                if (retryUsed.compareAndSet(false, true)) {
                    val fallbackOnDevice = !onDeviceMode.get() && canUseOnDevice()
                    if (onDeviceMode.get() || fallbackOnDevice) {
                        onDeviceMode.set(fallbackOnDevice)
                        Log.i(TAG, "Recognition error $error; retrying on ${recognizerLabel(fallbackOnDevice)}")
                        startRecognizer(recognizerRef, this, languageTag, onDevice = fallbackOnDevice)
                        return
                    }
                }

                complete(SttEvent.Failure(errorMessage(error)))
            }

            override fun onResults(results: Bundle?) {
                complete(SttEvent.Final(results.bestTranscript()))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults.bestTranscript()
                if (partial.isNotBlank()) {
                    trySend(SttEvent.Partial(partial))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        stopRequested = {
            mainHandler.post {
                recognizerRef.get()?.stopListening()
            }
        }

        startRecognizer(recognizerRef, listener, languageTag, onDevice = onDeviceMode.get())

        // Safety net: some recognizers never fire onError/onResults if the mic stalls. Force a
        // graceful stop after a hard cap so a session can't hang the UI in the recording state.
        val maxSessionStop = Runnable {
            if (!completed.get()) {
                Log.i(TAG, "Max session length reached; stopping recognizer")
                recognizerRef.get()?.stopListening()
            }
        }
        mainHandler.postDelayed(maxSessionStop, MAX_SESSION_LENGTH_MS)

        awaitClose {
            stopRequested = null
            mainHandler.removeCallbacks(maxSessionStop)
            mainHandler.post {
                recognizerRef.getAndSet(null)?.run {
                    setRecognitionListener(null)
                    cancel()
                    destroy()
                }
            }
        }
    }

    override fun stopListening() {
        stopRequested?.invoke()
    }

    private fun canUseOnDevice(): Boolean =
        onDeviceLanguageInstalled && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    private fun startRecognizer(
        recognizerRef: AtomicReference<SpeechRecognizer?>,
        listener: RecognitionListener,
        languageTag: String,
        onDevice: Boolean,
    ) {
        mainHandler.post {
            recognizerRef.getAndSet(null)?.run {
                setRecognitionListener(null)
                cancel()
                destroy()
            }
            val recognizer = if (onDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizerRef.set(recognizer)
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(recognitionIntent(languageTag, onDevice = onDevice))
        }
    }

    private fun recognitionIntent(languageTag: String, onDevice: Boolean = false): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Keep the recognizer's confidence-ranked alternatives so we can pick the best match.
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RECOGNITION_RESULTS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)

            // Language learners pause to think mid-sentence. The recognizer's default end-of-speech
            // silence windows are aggressive (~1s) and cut people off. Widen them so a natural pause
            // doesn't end the turn early, while still ending promptly after a clear, settled stop.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                COMPLETE_SILENCE_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_COMPLETE_SILENCE_MS,
            )
            // Don't finalize until the speaker has had a moment to begin; avoids empty "no match"
            // results when someone is still gathering the phrase.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MIN_SPEECH_LENGTH_MS,
            )

            // Only the dedicated on-device recognizer reads from the offline model store, so request
            // offline preference solely in that mode (forcing it on the default recognizer is the
            // unreliable path the class docs warn about).
            if (onDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    /**
     * Inspects on-device recognition support for [languageTag]. Updates [onDeviceLanguageInstalled]
     * and, when [triggerDownload] is set and the pack is missing, asks the system to download it.
     *
     * The system service owns the actual download, so this can only initiate and observe it.
     */
    private fun ensureOnDeviceSupport(languageTag: String, triggerDownload: Boolean) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            Log.i(TAG, "On-device recognition unavailable on this device")
            return
        }
        if (!supportCheckInFlight.compareAndSet(false, true)) return

        mainHandler.post {
            val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            val intent = recognitionIntent(languageTag)
            val executor = appContext.mainExecutor

            checker.checkRecognitionSupport(
                intent,
                executor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val installed = recognitionSupport.installedOnDeviceLanguages
                            .any { it.matchesLanguage(languageTag) }
                        onDeviceLanguageInstalled = installed

                        when {
                            installed -> {
                                Log.i(TAG, "On-device pack installed for $languageTag")
                                supportCheckInFlight.set(false)
                                checker.destroy()
                            }

                            recognitionSupport.pendingOnDeviceLanguages
                                .any { it.matchesLanguage(languageTag) } -> {
                                Log.i(TAG, "On-device pack download already pending for $languageTag")
                                supportCheckInFlight.set(false)
                                checker.destroy()
                            }

                            triggerDownload -> {
                                Log.i(TAG, "Requesting on-device pack download for $languageTag")
                                checker.triggerModelDownload(
                                    intent,
                                    executor,
                                    downloadListener(languageTag, checker),
                                )
                            }

                            else -> {
                                supportCheckInFlight.set(false)
                                checker.destroy()
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "checkRecognitionSupport failed: $error")
                        supportCheckInFlight.set(false)
                        checker.destroy()
                    }
                },
            )
        }
    }

    private fun downloadListener(
        languageTag: String,
        checker: SpeechRecognizer,
    ): ModelDownloadListener = object : ModelDownloadListener {
        override fun onProgress(completedPercent: Int) {
            Log.d(TAG, "On-device pack download for $languageTag: $completedPercent%")
        }

        override fun onScheduled() {
            Log.i(TAG, "On-device pack download scheduled for $languageTag")
        }

        override fun onSuccess() {
            Log.i(TAG, "On-device pack installed for $languageTag")
            onDeviceLanguageInstalled = true
            supportCheckInFlight.set(false)
            checker.destroy()
        }

        override fun onError(error: Int) {
            Log.w(TAG, "On-device pack download failed for $languageTag: $error")
            supportCheckInFlight.set(false)
            checker.destroy()
        }
    }

    private fun String.matchesLanguage(tag: String): Boolean =
        equals(tag, ignoreCase = true) ||
            substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true)

    private fun recognizerLabel(onDevice: Boolean): String = if (onDevice) "on-device" else "online"

    private fun Bundle?.bestTranscript(): String =
        this
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
        SpeechRecognizer.ERROR_NETWORK -> "Speech recognition network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech recognition service disconnected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many recognition requests — try again"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported on this device"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Spanish speech pack unavailable — install it in your device's voice settings"
        else -> "Speech recognition failed"
    }

    private companion object {
        private const val TAG = "AndroidSpeechToText"
        private const val MAX_RECOGNITION_RESULTS = 5
        private const val DEFAULT_LANGUAGE = "es-ES"

        // Silence the recognizer tolerates after speech looks finished before ending the turn.
        private const val COMPLETE_SILENCE_MS = 2_500L
        // Silence after speech *might* be finished; kept a touch shorter for responsiveness.
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 2_000L
        // Minimum capture window so a slow start isn't finalized as an empty result.
        private const val MIN_SPEECH_LENGTH_MS = 2_000L
        // Absolute cap on a single recording session as a hang guard.
        private const val MAX_SESSION_LENGTH_MS = 60_000L
    }
}
