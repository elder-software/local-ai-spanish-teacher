package com.eldersoftware.anytimespanish.data.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import mx.valdora.whisper.WhisperContext

/**
 * On-device, offline speech-to-text powered by whisper.cpp.
 * Downloads ggml-base.bin model at runtime and transcribes captured WAV audio.
 */
class WhisperSpeechToTextManager(private val context: Context) : SpeechToTextEngine {
    private val audioRecorder = AudioRecorder(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelFile = File(context.filesDir, "whisper/ggml-base.bin")
    private var whisperContext: WhisperContext? = null
    private var onStopRequested: (() -> Unit)? = null

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
                    trySend(SttEvent.Partial("Downloading Whisper model ($progress%)…"))
                }
            } catch (e: Exception) {
                trySend(SttEvent.Failure("Failed to download Whisper model: ${e.message}"))
                close()
                return@callbackFlow
            }
        }

        try {
            ensureModelLoaded()
        } catch (e: Exception) {
            trySend(SttEvent.Failure("Failed to load Whisper engine: ${e.message}"))
            close()
            return@callbackFlow
        }

        val activeContext = whisperContext ?: run {
            trySend(SttEvent.Failure("Failed to load Whisper engine: Context not initialized"))
            close()
            return@callbackFlow
        }

        onStopRequested = {
            launch {
                trySend(SttEvent.Partial("Transcribing…"))
                val stopResult = audioRecorder.stopAndGetWav()
                if (stopResult.isFailure) {
                    trySend(SttEvent.Failure(stopResult.exceptionOrNull()?.message ?: "Failed to stop recording"))
                    close()
                    return@launch
                }

                val wavBytes = stopResult.getOrNull()
                if (wavBytes == null || wavBytes.isEmpty()) {
                    trySend(SttEvent.Failure("No audio recorded"))
                    close()
                    return@launch
                }

                val wavFile = File(context.cacheDir, "whisper-input.wav")
                try {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(wavFile).use { fos ->
                            fos.write(wavBytes)
                        }
                    }
                    val text = withContext(Dispatchers.Default) {
                        activeContext.transcribe(wavFile)
                    }
                    trySend(SttEvent.Final(text.trim()))
                } catch (e: Exception) {
                    trySend(SttEvent.Failure("Transcription failed: ${e.message}"))
                } finally {
                    if (wavFile.exists()) {
                        wavFile.delete()
                    }
                    close()
                }
            }
        }

        val startResult = audioRecorder.start { chunk ->
            trySend(SttEvent.AudioLevel(computeNormalizedRms(chunk)))
        }
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

    override fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    override fun downloadModel(): Flow<Int> = flow {
        modelFile.parentFile?.mkdirs()
        val tempFile = File(context.cacheDir, "ggml-tiny.bin.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

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
            FileOutputStream(tempFile).use { output ->
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

        if (modelFile.exists()) {
            modelFile.delete()
        }
        if (!tempFile.renameTo(modelFile)) {
            tempFile.delete()
            throw IllegalStateException("Failed to move model to ${modelFile.absolutePath}")
        }

        emit(100)
    }.flowOn(Dispatchers.IO)

    @Synchronized
    private fun ensureModelLoaded() {
        if (whisperContext != null) return
        Log.d(TAG, "Loading Whisper model from ${modelFile.absolutePath}")
        whisperContext = WhisperContext(modelFile.absolutePath)
        Log.d(TAG, "Whisper model loaded successfully")
    }

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
        private const val TAG = "WhisperSpeechToText"
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    }
}
