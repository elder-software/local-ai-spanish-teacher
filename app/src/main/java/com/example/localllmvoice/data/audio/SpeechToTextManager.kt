package com.example.localllmvoice.data.audio

import android.content.Context
import android.util.Log
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * On-device, 100% offline speech-to-text powered by Moonshine Voice.
 * This class captures microphone input using [AudioRecorder], encodes it,
 * and runs inference on-device using the Moonshine Spanish Base model.
 */
class SpeechToTextManager(private val context: Context) : SpeechToTextEngine {
    private val audioRecorder = AudioRecorder(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var transcriber: Transcriber? = null
    private var onStopRequested: (() -> Unit)? = null

    // Spanish Base model files (58M parameters, optimized for offline transcription)
    private val modelDir = File(context.filesDir, "moonshine/base-es")
    private val modelFiles = listOf(
        ModelFile("encoder_model.ort", "https://download.moonshine.ai/model/base-es/quantized/base-es/encoder_model.ort", 20_000_000L),
        ModelFile("decoder_model_merged.ort", "https://download.moonshine.ai/model/base-es/quantized/base-es/decoder_model_merged.ort", 40_000_000L),
        ModelFile("tokenizer.bin", "https://download.moonshine.ai/model/base-es/quantized/base-es/tokenizer.bin", 200_000L)
    )

    private data class ModelFile(val name: String, val url: String, val minSize: Long)

    override fun hasRecordPermission(): Boolean = audioRecorder.hasRecordPermission()

    override fun isAvailable(): Boolean = true

    /**
     * Starts recording audio. When the recording is stopped via [stopListening], the audio is
     * processed fully on-device using the Moonshine STT model.
     */
    override fun transcribe(languageTag: String): Flow<SttEvent> = callbackFlow {
        if (!hasRecordPermission()) {
            trySend(SttEvent.Failure("Microphone permission not granted"))
            close()
            return@callbackFlow
        }

        // Ensure model is downloaded and loaded
        if (!isModelDownloaded()) {
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
            ensureTranscriberLoaded()
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
                    val text = performOnDeviceTranscription(pcmBytes)
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

    /** Forces the recorder to stop and triggers the transcription flow. */
    override fun stopListening() {
        onStopRequested?.invoke()
    }

    private fun isModelDownloaded(): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        return modelFiles.all { file ->
            val f = File(modelDir, file.name)
            f.isFile && f.length() >= file.minSize
        }
    }

    private suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val totalBytesToDownload = modelFiles.sumOf { it.minSize }
        var totalBytesDownloaded = 0L

        modelFiles.forEach { modelFile ->
            val targetFile = File(modelDir, modelFile.name)
            if (targetFile.isFile && targetFile.length() >= modelFile.minSize) {
                totalBytesDownloaded += targetFile.length()
                return@forEach
            }

            val request = Request.Builder().url(modelFile.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val stream = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                FileOutputStream(targetFile).use { output ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalBytesDownloaded += read
                        val percent = ((totalBytesDownloaded * 100) / totalBytesToDownload).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(percent.coerceIn(0, 99))
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            onProgress(100)
        }
    }

    private fun ensureTranscriberLoaded() {
        if (transcriber != null) return
        val t = Transcriber()
        t.loadFromFiles(modelDir.absolutePath, JNI.MOONSHINE_MODEL_ARCH_BASE)
        transcriber = t
        Log.i(TAG, "Moonshine Spanish Base model loaded successfully")
    }

    private suspend fun performOnDeviceTranscription(pcmBytes: ByteArray): String = withContext(Dispatchers.Default) {
        val activeTranscriber = transcriber ?: throw IllegalStateException("Transcriber not initialized")
        val floats = byteArrayToNormalizedFloatArray(pcmBytes)

        val resultText = StringBuilder()
        val lock = Any()

        activeTranscriber.addListener { event ->
            event.accept(object : TranscriptEvent.Visitor {
                override fun onLineStarted(e: TranscriptEvent.LineStarted) {}
                override fun onLineTextChanged(e: TranscriptEvent.LineTextChanged) {
                    synchronized(lock) {
                        // Keep track of active text changes if needed
                    }
                }
                override fun onLineCompleted(e: TranscriptEvent.LineCompleted) {
                    synchronized(lock) {
                        resultText.append(e.line.text).append(" ")
                    }
                }
                override fun onLineUpdated(e: TranscriptEvent.LineUpdated) {}
                override fun onError(e: TranscriptEvent.Error) {
                    Log.e(TAG, "Transcription error event: ${e.cause.message}", e.cause)
                }
            })
        }

        activeTranscriber.start()
        activeTranscriber.addAudio(floats, AudioRecorder.SAMPLE_RATE)
        activeTranscriber.stop()

        synchronized(lock) {
            resultText.toString().trim()
        }
    }

    private fun byteArrayToNormalizedFloatArray(pcmBytes: ByteArray): FloatArray {
        val shortCount = pcmBytes.size / 2
        val floats = FloatArray(shortCount)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            val shortVal = buffer.short
            floats[i] = shortVal.toFloat() / 32768.0f
        }
        return floats
    }

    companion object {
        private const val TAG = "SpeechToTextManager"
    }
}
