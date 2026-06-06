package com.example.localllmvoice.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ai.moonshine.voice.JNI
import ai.moonshine.voice.MicTranscriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class MoonshineSpeechToTextManager(private val context: Context) : SpeechToTextEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelDir = File(context.filesDir, "moonshine/spanish-base")
    private var cachedTranscriber: MicTranscriber? = null
    private var activeTranscriber: MicTranscriber? = null
    private var onStopRequested: (() -> Unit)? = null

    override fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun isAvailable(): Boolean = true

    override fun transcribe(languageTag: String): Flow<SttEvent> = callbackFlow {
        if (!hasRecordPermission()) {
            trySend(SttEvent.Failure("Microphone permission not granted"))
            close()
            return@callbackFlow
        }

        if (!isModelReady()) {
            try {
                downloadModel().collect { progress ->
                    trySend(SttEvent.Partial("Downloading Spanish STT model ($progress%)…"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                trySend(SttEvent.Failure("Failed to download STT model: ${e.message}"))
                close()
                return@callbackFlow
            }
        }

        try {
            val mic = getOrCreateTranscriber()
            activeTranscriber = mic

            var confirmedText = ""
            var currentLineText = ""
            var lastEmittedText = ""
            // A line is "active" once Moonshine starts decoding speech and until it reports the
            // line as completed. stop() flushes any active line, so we wait for that completion
            // event to emit the final, settled transcript instead of guessing with a timer.
            var lineActive = false
            var finalizing = false
            var finalized = false
            // Completed when the flushed line reports completion after stop(), so the finalizer
            // wakes the moment the transcript settles rather than waiting for a fixed timer.
            val lineCompletionSignal = CompletableDeferred<Unit>()

            fun emitFinalAndClose() {
                if (finalized) return
                finalized = true
                val finalText = listOf(confirmedText, currentLineText)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()
                trySend(SttEvent.Final(finalText))
                close()
            }

            val listener = Consumer<TranscriptEvent> { event: TranscriptEvent ->
                event.accept(object : TranscriptEventListener() {
                    override fun onLineStarted(e: TranscriptEvent.LineStarted) {
                        Log.d(TAG, "Line started: ${e.line.text}")
                        lineActive = true
                    }

                    override fun onLineTextChanged(e: TranscriptEvent.LineTextChanged) {
                        val lineText = e.line.text?.trim() ?: ""
                        currentLineText = lineText
                        val displayText = listOf(confirmedText, lineText)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        if (displayText.isNotEmpty() && displayText != lastEmittedText) {
                            lastEmittedText = displayText
                            trySend(SttEvent.Partial(displayText))
                        }
                    }

                    override fun onLineCompleted(e: TranscriptEvent.LineCompleted) {
                        val lineText = e.line.text?.trim() ?: ""
                        Log.d(TAG, "Line completed: $lineText")
                        if (lineText.isNotBlank()) {
                            confirmedText = listOf(confirmedText, lineText)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                            currentLineText = ""
                        }
                        lineActive = false
                        if (finalizing) {
                            lineCompletionSignal.complete(Unit)
                        }
                    }

                    override fun onError(e: TranscriptEvent.Error) {
                        Log.e(TAG, "Transcription error", e.cause)
                        trySend(SttEvent.Failure(e.cause?.message ?: "Transcription error"))
                    }
                })
            }

            onStopRequested = {
                Log.d(TAG, "Stop requested, finalizing transcription")
                val hadActiveLine = lineActive
                finalizing = true
                // Flushes the active line, which triggers onLineCompleted.
                mic.stop()
                if (!hadActiveLine) {
                    // No line was in progress, so there is nothing left to settle.
                    emitFinalAndClose()
                } else {
                    // Wait for the completion event, but cap the wait so a missed callback
                    // can never hang the finalizer indefinitely.
                    launch {
                        withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { lineCompletionSignal.await() }
                            ?: Log.w(TAG, "Timed out waiting for line completion; finalizing anyway")
                        emitFinalAndClose()
                    }
                }
            }

            mic.addListener(listener)
            mic.start()

            trySend(SttEvent.Partial("Listening…"))

            awaitClose {
                Log.d(TAG, "Cleaning up transcription resources")
                onStopRequested = null
                mic.stop()
                mic.removeListener(listener)
                activeTranscriber = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transcription", e)
            trySend(SttEvent.Failure("Failed to start STT: ${e.message}"))
            close()
        }
    }

    @Synchronized
    private fun getOrCreateTranscriber(): MicTranscriber {
        cachedTranscriber?.let { return it }
        
        Log.d(TAG, "Loading Moonshine model from ${modelDir.absolutePath}")
        val mic = MicTranscriber()
        mic.loadFromFiles(modelDir.absolutePath, JNI.MOONSHINE_MODEL_ARCH_BASE)
        mic.onMicPermissionGranted()
        cachedTranscriber = mic
        Log.d(TAG, "Moonshine model loaded successfully")
        return mic
    }

    override fun preload() {
        if (!isModelReady()) return
        Thread {
            try {
                getOrCreateTranscriber()
                Log.i(TAG, "Model pre-loaded successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-load model", e)
            }
        }.start()
    }

    override fun stopListening() {
        onStopRequested?.invoke()
    }

    override fun isModelReady(): Boolean {
        val encoder = File(modelDir, "encoder_model.ort")
        val decoder = File(modelDir, "decoder_model_merged.ort")
        val tokenizer = File(modelDir, "tokenizer.bin")
        return encoder.exists() && decoder.exists() && tokenizer.exists()
    }

    override fun downloadModel(): Flow<Int> = flow {
        modelDir.mkdirs()

        val files = listOf(
            "encoder_model.ort" to ENCODER_URL,
            "decoder_model_merged.ort" to DECODER_URL,
            "tokenizer.bin" to TOKENIZER_URL,
        )

        var completedFiles = 0
        for ((filename, url) in files) {
            val destFile = File(modelDir, filename)
            if (destFile.exists()) {
                completedFiles++
                continue
            }

            Log.d(TAG, "Downloading $filename from $url")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed for $filename: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response for $filename")
                val contentLength = body.contentLength()
                val stream = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var totalBytesDownloaded = 0L

                val tempFile = File(modelDir, "$filename.tmp")
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalBytesDownloaded += read

                        if (contentLength > 0) {
                            val fileProgress = ((totalBytesDownloaded * 100) / contentLength).toInt()
                            val overallProgress = ((completedFiles * 100 + fileProgress) / files.size)
                            emit(overallProgress.coerceIn(0, 99))
                        }
                    }
                }
                if (!tempFile.renameTo(destFile)) {
                    tempFile.delete()
                    throw IllegalStateException("Failed to save $filename")
                }
            }
            completedFiles++
        }

        emit(100)
        Log.i(TAG, "Moonshine Spanish model downloaded successfully")
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "MoonshineSttManager"
        // Upper bound on how long to wait for the final line-completion event after stop().
        private const val FINALIZE_TIMEOUT_MS = 2_000L
        private const val MODEL_BASE_URL = "https://download.moonshine.ai/model/base-es/quantized/base-es"
        private const val ENCODER_URL = "$MODEL_BASE_URL/encoder_model.ort"
        private const val DECODER_URL = "$MODEL_BASE_URL/decoder_model_merged.ort"
        private const val TOKENIZER_URL = "$MODEL_BASE_URL/tokenizer.bin"
    }
}
