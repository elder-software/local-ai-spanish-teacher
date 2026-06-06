package com.example.localllmvoice.data.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * Captures microphone input via [AudioRecorder] and streams PCM chunks into Vosk for live partials.
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
                downloadModel().collect { progress ->
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

        val activeModel = model ?: run {
            trySend(SttEvent.Failure("Failed to load STT engine: Model not initialized"))
            close()
            return@callbackFlow
        }

        val recognizer = Recognizer(activeModel, AudioRecorder.SAMPLE_RATE.toFloat())
        val pcmChunks = Channel<ByteArray>(Channel.UNLIMITED)
        var confirmedText = ""
        var lastPartialText = ""

        val recognitionJob: Job = launch(Dispatchers.Default) {
            for (chunk in pcmChunks) {
                val accepted = recognizer.acceptWaveForm(chunk, chunk.size)
                val displayText = if (accepted) {
                    val segment = parseVoskText(recognizer.result, "text")
                    if (segment.isNotBlank()) {
                        confirmedText = listOf(confirmedText, segment)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                    confirmedText
                } else {
                    val partial = parseVoskText(recognizer.partialResult, "partial")
                    listOf(confirmedText, partial)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }

                if (displayText.isNotBlank() && displayText != lastPartialText) {
                    lastPartialText = displayText
                    trySend(SttEvent.Partial(displayText))
                }
            }
        }

        onStopRequested = {
            launch {
                trySend(SttEvent.Partial("Transcribing…"))
                val stopResult = audioRecorder.stopAndGetRawPcm()
                if (stopResult.isFailure) {
                    trySend(SttEvent.Failure(stopResult.exceptionOrNull()?.message ?: "Failed to stop recording"))
                    close()
                    return@launch
                }

                pcmChunks.close()
                recognitionJob.join()

                val finalSegment = parseVoskText(recognizer.finalResult, "text")
                val finalText = listOf(confirmedText, finalSegment)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()

                trySend(SttEvent.Final(finalText))
                close()
            }
        }

        val startResult = audioRecorder.start { chunk ->
            pcmChunks.trySend(chunk)
            trySend(SttEvent.AudioLevel(computeNormalizedRms(chunk)))
        }
        if (startResult.isFailure) {
            pcmChunks.close()
            recognitionJob.cancel()
            recognizer.close()
            trySend(SttEvent.Failure(startResult.exceptionOrNull()?.message ?: "Failed to start recording"))
            close()
            return@callbackFlow
        }

        trySend(SttEvent.Partial("Listening…"))

        awaitClose {
            onStopRequested = null
            pcmChunks.close()
            recognitionJob.cancel()
            recognizer.close()
            audioRecorder.cancel()
        }
    }

    override fun stopListening() {
        onStopRequested?.invoke()
    }

    override fun isModelReady(): Boolean {
        return File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory
    }

    override fun downloadModel(): Flow<Int> = flow {
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
                    emit(percent.coerceIn(0, 99))
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

        emit(100)
    }.flowOn(Dispatchers.IO)

    private fun ensureModelLoaded() {
        if (model != null) return
        model = Model(modelDir.absolutePath)
        Log.i(TAG, "Vosk Spanish model loaded successfully")
    }

    private fun parseVoskText(json: String, field: String): String =
        JSONObject(json).optString(field).trim()

    /** Root-mean-square amplitude of a PCM16 little-endian chunk, normalised to 0f..1f. */
    private fun computeNormalizedRms(chunk: ByteArray): Float {
        val sampleCount = chunk.size / 2
        if (sampleCount == 0) return 0f
        var sumSquares = 0.0
        var index = 0
        while (index + 1 < chunk.size) {
            val low = chunk[index].toInt() and 0xFF
            val high = chunk[index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            sumSquares += (sample.toDouble() * sample.toDouble())
            index += 2
        }
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "VoskSpeechToTextManager"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        private const val MODEL_ZIP_NAME = "vosk-model-small-es-0.42"
    }
}
