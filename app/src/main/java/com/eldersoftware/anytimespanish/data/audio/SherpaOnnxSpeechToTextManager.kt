package com.eldersoftware.anytimespanish.data.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * On-device, offline streaming speech-to-text powered by sherpa-onnx
 * (https://github.com/k2-fsa/sherpa-onnx).
 *
 * Uses the streaming Zipformer transducer
 * `sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06`, a Spanish-specific streaming model
 * trained on the Kroko corpus. Streaming decode runs on a dedicated feed thread, mirroring the
 * Moonshine manager's architecture: the mic capture callback only enqueues PCM and the feed
 * thread submits it to the recognizer so JNI decoding never stalls capture. Endpoint detection
 * is left enabled with tuned silence rules so completed utterances are surfaced as confirmed
 * text and the stream reset for the next utterance, while partials stream continuously.
 */
class SherpaOnnxSpeechToTextManager(context: Context) : SpeechToTextEngine {
    private val audioRecorder = AudioRecorder(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Files for this model are stored directly under modelDir (no per-exp subfolder), matching
    // the layout the HuggingFace repo exposes: encoder.onnx / decoder.onnx / joiner.onnx /
    // tokens.txt all at the repo root.
    private val modelDir = File(context.filesDir, "sherpa-onnx/streaming-zipformer-es-kroko")

    @Volatile
    private var cachedRecognizer: OnlineRecognizer? = null
    private val recognizerLock = Any()

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

        val recognizer = try {
            getOrCreateRecognizer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer", e)
            trySend(SttEvent.Failure("Failed to initialize STT: ${e.message}"))
            close()
            return@callbackFlow
        }

        val stream = recognizer.createStream()

        var confirmedText = ""
        val lastEmittedPartial = ""
        val finalized = false
        val feeding = AtomicBoolean(true)

        // Bridges the capture thread to the inference thread. addAudio-equivalent (decode) blocks
        // on JNI, so we must not run it inline in the mic callback. A blocking queue lets the feed
        // thread park until samples arrive instead of busy-polling, keeping decode latency minimal.
        val audioQueue = LinkedBlockingQueue<FloatArray>()

        fun emitPartial(text: String) {
            if (text.isEmpty() || text == lastEmittedPartial) return
            trySend(SttEvent.Partial(text))
        }

        fun emitFinalAndClose(text: String) {
            if (finalized) return
            trySend(SttEvent.Final(text.trim()))
            close()
        }

        fun drainQueuedAudio(timeoutMs: Long = 0): FloatArray {
            val first = if (timeoutMs > 0) {
                audioQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                audioQueue.poll()
            } ?: return EMPTY_AUDIO
            val chunks = ArrayList<FloatArray>()
            chunks.add(first)
            var total = first.size
            while (true) {
                val chunk = audioQueue.poll() ?: break
                chunks.add(chunk)
                total += chunk.size
            }
            val merged = FloatArray(total)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.size)
                offset += chunk.size
            }
            return merged
        }

        fun decodeAvailable(recognizer: OnlineRecognizer, stream: OnlineStream) {
            // Decode as long as the model has enough frames queued. Each decode call advances the
            // transducer by one chunk; we loop so the recognizer catches up to the newly fed audio
            // before polling for more, keeping latency low.
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            val result = recognizer.getResult(stream)
            val partial = result.text.trim()
            val display = listOf(confirmedText, partial)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            emitPartial(display)

            // Endpoint detection signals a completed utterance (trailing silence). We commit the
            // current partial as confirmed text and reset the stream so the next utterance starts
            // fresh without re-decoding committed history.
            if (recognizer.isEndpoint(stream)) {
                if (partial.isNotBlank()) {
                    confirmedText = listOf(confirmedText, partial)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                recognizer.reset(stream)
                emitPartial(confirmedText)
            }
        }

        val feedThread = Thread {
            while (feeding.get()) {
                val audio = try {
                    drainQueuedAudio(FEED_IDLE_POLL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                if (audio.isEmpty()) continue
                try {
                    stream.acceptWaveform(audio, AudioRecorder.SAMPLE_RATE)
                    decodeAvailable(recognizer, stream)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to feed/decode audio", e)
                }
            }
        }.apply {
            name = "sherpa-feed"
            start()
        }

        onStopRequested = {
            Log.d(TAG, "Stop requested, finalizing transcription")
            ioScope.launch {
                // Stop capture first so no further chunks enqueue, then halt the feed thread and
                // only then flush trailing audio. We discard the PCM: streaming already decoded
                // the audio incrementally. Joining the feed thread before signalling input-finished
                // guarantees acceptWaveform/decode/inputFinished never touch the stream concurrently.
                audioRecorder.stopAndDiscard()
                feeding.set(false)
                feedThread.interrupt()
                feedThread.join(FEED_JOIN_TIMEOUT_MS)

                val remaining = drainQueuedAudio()
                if (remaining.isNotEmpty()) {
                    try {
                        stream.acceptWaveform(remaining, AudioRecorder.SAMPLE_RATE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to feed trailing audio", e)
                    }
                }

                try {
                    // Signals that no more audio will arrive, flushing the final frames through the
                    // transducer so the last partial settles before we read the result.
                    stream.inputFinished()
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to finalize decode", e)
                }

                val trailing = recognizer.getResult(stream).text.trim()
                val finalText = listOf(confirmedText, trailing)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()

                runCatching { stream.release() }
                emitFinalAndClose(finalText)
            }
        }

        val startResult = audioRecorder.start { chunk ->
            audioQueue.add(pcm16ToFloat(chunk))
            trySend(SttEvent.AudioLevel(computeNormalizedRms(chunk)))
        }
        if (startResult.isFailure) {
            feeding.set(false)
            feedThread.interrupt()
            runCatching { stream.release() }
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
            runCatching { stream.release() }
        }
    }

    private fun getOrCreateRecognizer(): OnlineRecognizer {
        synchronized(recognizerLock) {
            cachedRecognizer?.let { return it }
            Log.d(TAG, "Loading sherpa-onnx model from ${modelDir.absolutePath}")
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = AudioRecorder.SAMPLE_RATE,
                    featureDim = FEATURE_DIM,
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, ENCODER_FILE).absolutePath,
                        decoder = File(modelDir, DECODER_FILE).absolutePath,
                        joiner = File(modelDir, JOINER_FILE).absolutePath,
                    ),
                    tokens = File(modelDir, TOKENS_FILE).absolutePath,
                    numThreads = NUM_THREADS,
                    modelType = MODEL_TYPE,
                ),
                endpointConfig = EndpointConfig(
                    // rule1: an utterance ends after this much trailing silence regardless of
                    // content. Tuned a bit tighter than the 2.4s default so short Spanish phrases
                    // commit promptly for a snappier language-learning UX.
                    rule1 = EndpointRule(false, minTrailingSilence = 1.6f, minUtteranceLength = 0.0f),
                    // rule2: trailing silence after speech has been detected; closes the utterance
                    // once the user pauses mid-sentence.
                    rule2 = EndpointRule(true, minTrailingSilence = 1.0f, minUtteranceLength = 0.0f),
                    // rule3: hard cap so very long continuous speech still segments.
                    rule3 = EndpointRule(false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f),
                ),
                enableEndpoint = true,
            )
            val recognizer = OnlineRecognizer(config = config)
            cachedRecognizer = recognizer
            Log.d(TAG, "sherpa-onnx model loaded successfully")
            return recognizer
        }
    }

    override fun preload() {
        if (!isModelReady()) return
        ioScope.launch {
            try {
                getOrCreateRecognizer()
                Log.i(TAG, "Model pre-loaded successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-load model", e)
            }
        }
    }

    override fun stopListening() {
        onStopRequested?.invoke()
    }

    override fun isModelReady(): Boolean {
        return File(modelDir, ENCODER_FILE).exists() &&
            File(modelDir, DECODER_FILE).exists() &&
            File(modelDir, JOINER_FILE).exists() &&
            File(modelDir, TOKENS_FILE).exists()
    }

    override fun downloadModel(): Flow<Int> = flow {
        modelDir.mkdirs()

        val files = listOf(
            ENCODER_FILE to ENCODER_URL,
            DECODER_FILE to DECODER_URL,
            JOINER_FILE to JOINER_URL,
            TOKENS_FILE to TOKENS_URL,
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
        Log.i(TAG, "sherpa-onnx Spanish model downloaded successfully")
    }.flowOn(Dispatchers.IO)

    /** Converts PCM16 little-endian bytes to the float[-1f, 1f] samples sherpa-onnx expects. */
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
        private const val TAG = "SherpaOnnxSttManager"
        private val EMPTY_AUDIO = FloatArray(0)

        // Mel feature dimension expected by the Zipformer transducer.
        private const val FEATURE_DIM = 80
        // Two decode threads is a reasonable latency/throughput tradeoff on modern phones; the
        // transducer is small enough that more threads give diminishing returns.
        private const val NUM_THREADS = 2
        private const val MODEL_TYPE = "zipformer2"

        // How long the feed thread blocks waiting for audio before re-checking `feeding`. Only a
        // backstop: interrupting the thread during unwind wakes the poll immediately.
        private const val FEED_IDLE_POLL_MS = 500L
        // Upper bound on joining the feed thread during finalization.
        private const val FEED_JOIN_TIMEOUT_MS = 1_000L

        private const val MODEL_DIR_NAME =
            "sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06"
        private const val MODEL_BASE_URL =
            "https://huggingface.co/csukuangfj/$MODEL_DIR_NAME/resolve/main"

        private const val ENCODER_FILE = "encoder.onnx"
        private const val DECODER_FILE = "decoder.onnx"
        private const val JOINER_FILE = "joiner.onnx"
        private const val TOKENS_FILE = "tokens.txt"

        private const val ENCODER_URL = "$MODEL_BASE_URL/$ENCODER_FILE"
        private const val DECODER_URL = "$MODEL_BASE_URL/$DECODER_FILE"
        private const val JOINER_URL = "$MODEL_BASE_URL/$JOINER_FILE"
        private const val TOKENS_URL = "$MODEL_BASE_URL/$TOKENS_FILE"
    }
}
