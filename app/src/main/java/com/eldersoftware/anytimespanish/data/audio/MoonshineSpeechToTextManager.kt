package com.eldersoftware.anytimespanish.data.audio

import android.content.Context
import android.util.Log
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriberOption
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.sqrt

class MoonshineSpeechToTextManager(private val context: Context) : SpeechToTextEngine {
    private val audioRecorder = AudioRecorder(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelDir = File(context.filesDir, "moonshine/spanish-base")
    private var cachedTranscriber: Transcriber? = null
    private var activeTranscriber: Transcriber? = null
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
            val transcriber = getOrCreateTranscriber()
            activeTranscriber = transcriber

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

            // Bridges the capture thread (AudioRecorder) to the inference thread. We must not run
            // Moonshine inference inline in the mic callback: addAudio() blocks on JNI decoding,
            // and stalling the capture loop would drop microphone samples. So the callback only
            // enqueues PCM and a dedicated feed thread coalesces and submits it.
            val audioQueue = ConcurrentLinkedQueue<FloatArray>()
            val feeding = AtomicBoolean(true)

            fun drainQueuedAudio(): FloatArray {
                val chunks = ArrayList<FloatArray>()
                var total = 0
                while (true) {
                    val chunk = audioQueue.poll() ?: break
                    chunks.add(chunk)
                    total += chunk.size
                }
                if (total == 0) return EMPTY_AUDIO
                val merged = FloatArray(total)
                var offset = 0
                for (chunk in chunks) {
                    System.arraycopy(chunk, 0, merged, offset, chunk.size)
                    offset += chunk.size
                }
                return merged
            }

            fun emitFinalAndClose(finalText: String) {
                if (finalized) return
                finalized = true
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

            transcriber.addListener(listener)
            transcriber.start()

            val feedThread = Thread {
                while (feeding.get()) {
                    val audio = drainQueuedAudio()
                    if (audio.isEmpty()) {
                        try {
                            Thread.sleep(FEED_IDLE_SLEEP_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        continue
                    }
                    try {
                        transcriber.addAudio(audio, AudioRecorder.SAMPLE_RATE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to feed audio to transcriber", e)
                    }
                }
            }.apply {
                name = "moonshine-feed"
                start()
            }

            onStopRequested = {
                Log.d(TAG, "Stop requested, finalizing transcription")
                // Runs on IO: feedThread.join and every transcriber call below block on JNI
                // decoding, and the second-pass decode of the full utterance is the heaviest
                // call in this file.
                launch(Dispatchers.IO) {
                    // Stop capture first so no further chunks are enqueued, then drain whatever is
                    // left, halt the feed thread, and only then flush the stream. Joining the feed
                    // thread before stop() guarantees addAudio() and stop() never touch the stream
                    // concurrently. The returned PCM is the complete utterance (silence-trimmed),
                    // kept for the second decoding pass below.
                    val fullUtterancePcm = audioRecorder
                        .stopAndGetRawPcm(maxDurationMs = FULL_UTTERANCE_MAX_MS)
                        .getOrNull()
                    feeding.set(false)
                    feedThread.interrupt()
                    feedThread.join(FEED_JOIN_TIMEOUT_MS)

                    val remaining = drainQueuedAudio()
                    if (remaining.isNotEmpty()) {
                        try {
                            transcriber.addAudio(remaining, AudioRecorder.SAMPLE_RATE)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to feed trailing audio", e)
                        }
                    }

                    val hadActiveLine = lineActive
                    finalizing = true
                    // Flushes the active line, which triggers onLineCompleted.
                    transcriber.stop()
                    if (hadActiveLine) {
                        // Wait for the completion event, but cap the wait so a missed callback
                        // can never hang the finalizer indefinitely.
                        withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { lineCompletionSignal.await() }
                            ?: Log.w(TAG, "Timed out waiting for line completion; finalizing anyway")
                    }

                    val streamingText = listOf(confirmedText, currentLineText)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .trim()

                    // Second pass: re-decode the whole utterance in one shot. Streaming decodes
                    // line by line (segmented by the internal VAD), so the model never sees full
                    // context; a single non-streaming decode over the complete audio is more
                    // accurate. The streaming text remains as a fallback if this pass fails.
                    val refinedText = fullUtterancePcm?.let { pcm ->
                        try {
                            transcribeFullUtterance(transcriber, pcm)
                        } catch (e: Exception) {
                            Log.w(TAG, "Full-utterance re-decode failed, using streaming text", e)
                            null
                        }
                    }

                    emitFinalAndClose(refinedText?.takeIf { it.isNotBlank() } ?: streamingText)
                }
            }

            val startResult = audioRecorder.start { chunk ->
                audioQueue.add(pcm16ToFloat(chunk))
                trySend(SttEvent.AudioLevel(computeNormalizedRms(chunk)))
            }
            if (startResult.isFailure) {
                feeding.set(false)
                feedThread.interrupt()
                trySend(SttEvent.Failure(startResult.exceptionOrNull()?.message ?: "Failed to start recording"))
                close()
                return@callbackFlow
            }

            trySend(SttEvent.Partial("Listening…"))

            awaitClose {
                Log.d(TAG, "Cleaning up transcription resources")
                onStopRequested = null
                feeding.set(false)
                feedThread.interrupt()
                audioRecorder.cancel()
                transcriber.removeListener(listener)
                activeTranscriber = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transcription", e)
            trySend(SttEvent.Failure("Failed to start STT: ${e.message}"))
            close()
        }
    }

    /**
     * Decodes the complete utterance in a single non-streaming pass and returns the joined
     * transcript, or null when there is nothing to decode. [Transcriber.transcribeWithoutStreaming]
     * is synchronous and does not touch the streaming state or notify listeners, so it is safe to
     * call on the shared transcriber once the stream has been stopped and the feed thread joined.
     */
    private fun transcribeFullUtterance(transcriber: Transcriber, pcm: ByteArray): String? {
        val audio = pcm16ToFloat(pcm)
        if (audio.isEmpty()) return null
        val transcript = transcriber.transcribeWithoutStreaming(audio, AudioRecorder.SAMPLE_RATE)
            ?: return null
        return transcript.lines
            .mapNotNull { it.text?.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }

    @Synchronized
    private fun getOrCreateTranscriber(): Transcriber {
        cachedTranscriber?.let { return it }

        Log.d(TAG, "Loading Moonshine model from ${modelDir.absolutePath}")
        val transcriber = Transcriber(buildTranscriberOptions())
        transcriber.loadFromFiles(modelDir.absolutePath, JNI.MOONSHINE_MODEL_ARCH_BASE)
        cachedTranscriber = transcriber
        Log.d(TAG, "Moonshine model loaded successfully")
        return transcriber
    }

    /**
     * Native ONNX Runtime options forwarded to the transcriber at load time. Kept empty by
     * default because unknown keys cause native load to fail (loadFromFiles throws). This is the
     * single place to experiment with execution-provider / threading knobs once a key is confirmed
     * to be honored by the prebuilt moonshine-jni native library, e.g.
     * `TranscriberOption("intra_op_num_threads", "4")`.
     */
    private fun buildTranscriberOptions(): List<TranscriberOption> = emptyList()

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

    /** Converts PCM16 little-endian bytes to the float[-1f, 1f] samples Moonshine expects. */
    private fun pcm16ToFloat(chunk: ByteArray): FloatArray {
        val sampleCount = chunk.size / 2
        if (sampleCount == 0) return EMPTY_AUDIO
        val out = FloatArray(sampleCount)
        var index = 0
        while (index < sampleCount) {
            val low = chunk[2 * index].toInt() and 0xFF
            val high = chunk[2 * index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            out[index] = sample / 32768f
            index++
        }
        return out
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
        val rms = sqrt(sumSquares / sampleCount)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "MoonshineSttManager"
        private val EMPTY_AUDIO = FloatArray(0)
        // Upper bound on how long to wait for the final line-completion event after stop().
        private const val FINALIZE_TIMEOUT_MS = 2_000L
        // How long the feed thread parks when no audio is queued before checking again.
        private const val FEED_IDLE_SLEEP_MS = 20L
        // Upper bound on joining the feed thread during finalization.
        private const val FEED_JOIN_TIMEOUT_MS = 1_000L
        // Cap on the audio kept for the second (full-utterance) decoding pass. Much higher than
        // the trimmer's default 8s so long answers are not truncated before re-decoding.
        private const val FULL_UTTERANCE_MAX_MS = 120_000
        private const val MODEL_BASE_URL = "https://download.moonshine.ai/model/base-es/quantized/base-es"
        private const val ENCODER_URL = "$MODEL_BASE_URL/encoder_model.ort"
        private const val DECODER_URL = "$MODEL_BASE_URL/decoder_model_merged.ort"
        private const val TOKENIZER_URL = "$MODEL_BASE_URL/tokenizer.bin"
    }
}
