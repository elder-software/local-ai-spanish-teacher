package com.example.localllmvoice.data.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * On-device, offline speech-to-text powered by Vosk.
 * Captures microphone input via [AudioRecorder], then transcribes the recorded PCM buffer.
 */
class VoskSpeechToTextManager(private val context: Context) : SpeechToTextEngine {
    private val audioRecorder = AudioRecorder(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var model: Model? = null
    private var onStopRequested: (() -> Unit)? = null
    private val modelDir = File(context.filesDir, "vosk/small-es")

    override fun hasRecordPermission(): Boolean = audioRecorder.hasRecordPermission()

    override fun isAvailable(): Boolean = true

    override fun transcribe(languageTag: String): Flow<SttEvent> = callbackFlow {
        if (!hasRecordPermission()) {
            trySend(SttEvent.Failure("Microphone permission not granted"))
            close()
            return@callbackFlow
        }

        if (!isModelReady()) {
            try {
                downloadModel { progress ->
                    trySend(SttEvent.Partial("Downloading Spanish STT model ($progress%)…"))
                }
            } catch (e: Exception) {
                trySend(SttEvent.Failure("Failed to download STT model: ${e.message}"))
                close()
                return@callbackFlow
            }
        }

        try {
            ensureModelLoaded()
        } catch (e: Exception) {
            trySend(SttEvent.Failure("Failed to load STT engine: ${e.message}"))
            close()
            return@callbackFlow
        }

        onStopRequested = {
            launch {
                trySend(SttEvent.Partial("Transcribing…"))
                val pcmResult = audioRecorder.stopAndGetRawPcm()
                if (pcmResult.isFailure) {
                    trySend(SttEvent.Failure(pcmResult.exceptionOrNull()?.message ?: "Failed to stop recording"))
                    close()
                    return@launch
                }

                val pcmBytes = pcmResult.getOrThrow()
                if (pcmBytes.isEmpty()) {
                    trySend(SttEvent.Failure("No audio recorded"))
                    close()
                    return@launch
                }

                try {
                    val text = performTranscription(pcmBytes)
                    trySend(SttEvent.Final(text))
                } catch (e: Exception) {
                    trySend(SttEvent.Failure(e.message ?: "Transcription failed"))
                } finally {
                    close()
                }
            }
        }

        val startResult = audioRecorder.start()
        if (startResult.isFailure) {
            trySend(SttEvent.Failure(startResult.exceptionOrNull()?.message ?: "Failed to start recording"))
            close()
            return@callbackFlow
        }

        trySend(SttEvent.Partial("Listening…"))

        awaitClose {
            onStopRequested = null
            audioRecorder.cancel()
        }
    }

    override fun stopListening() {
        onStopRequested?.invoke()
    }

    private fun isModelReady(): Boolean {
        return File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory
    }

    private suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "vosk-model.zip")
        val request = Request.Builder().url(MODEL_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLength = body.contentLength()
            val stream = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var totalBytesDownloaded = 0L
            FileOutputStream(zip).use { output ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalBytesDownloaded += read
                    val percent = if (contentLength > 0) {
                        ((totalBytesDownloaded * 100) / contentLength).toInt()
                    } else {
                        0
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(percent.coerceIn(0, 99))
                    }
                }
            }
        }

        val voskDir = context.filesDir.resolve("vosk")
        ZipExtractor.unzip(zip, voskDir)

        val extractedModel = File(voskDir, MODEL_ZIP_NAME)
        if (!extractedModel.isDirectory) {
            throw IllegalStateException("Expected model folder not found: $MODEL_ZIP_NAME")
        }

        modelDir.parentFile?.mkdirs()
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        if (!extractedModel.renameTo(modelDir)) {
            throw IllegalStateException("Failed to move model to ${modelDir.absolutePath}")
        }

        zip.delete()

        withContext(Dispatchers.Main) {
            onProgress(100)
        }
    }

    private fun ensureModelLoaded() {
        if (model != null) return
        model = Model(modelDir.absolutePath)
        Log.i(TAG, "Vosk Spanish model loaded successfully")
    }

    private suspend fun performTranscription(pcmBytes: ByteArray): String = withContext(Dispatchers.Default) {
        val activeModel = model ?: throw IllegalStateException("Model not initialized")
        val recognizer = Recognizer(activeModel, AudioRecorder.SAMPLE_RATE.toFloat())
        try {
            val chunkSize = 4096
            var offset = 0
            while (offset < pcmBytes.size) {
                val end = minOf(offset + chunkSize, pcmBytes.size)
                val chunk = pcmBytes.copyOfRange(offset, end)
                recognizer.acceptWaveForm(chunk, chunk.size)
                offset = end
            }
            val finalJson = recognizer.finalResult
            JSONObject(finalJson).optString("text").trim()
        } finally {
            recognizer.close()
        }
    }

    companion object {
        private const val TAG = "VoskSpeechToTextManager"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        private const val MODEL_ZIP_NAME = "vosk-model-small-es-0.42"
    }
}
