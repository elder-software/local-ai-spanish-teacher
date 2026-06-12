package com.eldersoftware.anytimespanish.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AudioRecorder(private val context: Context) {
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorderMutex = Mutex()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private val audioEffects = mutableListOf<AudioEffect>()

    // True while the hardware AutomaticGainControl effect is attached to the active session.
    // When the HAL is already managing input level, applying our own fixed boost on top risks
    // pushing loud speech into the limiter, and clipping distortion is far worse for ASR than
    // a quiet signal.
    @Volatile
    private var hardwareAgcActive = false

    val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun start(onPcmChunk: ((ByteArray) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            return@withContext Result.failure(SecurityException("RECORD_AUDIO not granted"))
        }
        stopActiveRecording(clearBuffer = true)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return@withContext Result.failure(IllegalStateException("Invalid AudioRecord buffer size"))
        }

        val record = AudioRecord(
            // VOICE_RECOGNITION is tuned for ASR: it applies speech-optimized pre-processing
            // and avoids the aggressive, music-oriented AGC that the generic MIC source uses.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext Result.failure(IllegalStateException("AudioRecord failed to initialize"))
        }

        enableAudioEffects(record.audioSessionId)

        record.startRecording()
        recorderMutex.withLock {
            audioRecord = record
            recordingJob = recorderScope.launch {
                captureAudio(record, onPcmChunk)
            }
        }
        Result.success(Unit)
    }

    suspend fun stopAndGetWav(): Result<ByteArray> = withContext(Dispatchers.IO) {
        val wasRecording = recorderMutex.withLock { audioRecord != null }
        if (!wasRecording) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }
        stopActiveRecording(clearBuffer = false)
        val wav = synchronized(pcmBuffer) {
            val trimmedPcm = Pcm16AudioTrimmer.trim(pcmBuffer.toByteArray())
            val encodedWav = WavEncoder.encodePcm16(trimmedPcm)
            pcmBuffer.reset()
            encodedWav
        }
        Result.success(wav)
    }

    suspend fun stopAndGetRawPcm(
        maxDurationMs: Int = Pcm16AudioTrimmer.DEFAULT_MAX_DURATION_MS,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val wasRecording = recorderMutex.withLock { audioRecord != null }
        if (!wasRecording) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }
        stopActiveRecording(clearBuffer = false)
        val pcm = synchronized(pcmBuffer) {
            val trimmedPcm = Pcm16AudioTrimmer.trim(pcmBuffer.toByteArray(), maxDurationMs)
            pcmBuffer.reset()
            trimmedPcm
        }
        Result.success(pcm)
    }

    fun cancel() {
        recorderScope.launch {
            stopActiveRecording(clearBuffer = true)
        }
    }

    private suspend fun stopActiveRecording(clearBuffer: Boolean) {
        val (record, job) = recorderMutex.withLock {
            val activeRecord = audioRecord
            val activeJob = recordingJob
            audioRecord = null
            recordingJob = null
            activeRecord to activeJob
        }

        record ?: run {
            if (clearBuffer) {
                synchronized(pcmBuffer) { pcmBuffer.reset() }
            }
            return
        }

        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { record.stop() }
        }
        job?.join()
        releaseAudioEffects()
        record.release()

        if (clearBuffer) {
            synchronized(pcmBuffer) { pcmBuffer.reset() }
        }
    }

    /**
     * Attaches built-in DSP effects to the capture session when the hardware/OS supports them.
     * These run in the audio HAL (cheap) and meaningfully improve SNR for far-field/noisy input,
     * which directly improves speech recognition accuracy.
     */
    private fun enableAudioEffects(sessionId: Int) {
        if (sessionId == AudioRecord.ERROR || sessionId == AudioRecord.ERROR_BAD_VALUE) return

        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.let {
                    it.enabled = true
                    audioEffects += it
                }
            }
        }.onFailure { Log.w(TAG, "NoiseSuppressor unavailable", it) }

        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.let {
                    it.enabled = true
                    audioEffects += it
                    hardwareAgcActive = true
                }
            }
        }.onFailure { Log.w(TAG, "AutomaticGainControl unavailable", it) }

        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.let {
                    it.enabled = true
                    audioEffects += it
                }
            }
        }.onFailure { Log.w(TAG, "AcousticEchoCanceler unavailable", it) }
    }

    private fun releaseAudioEffects() {
        audioEffects.forEach { effect ->
            runCatching {
                effect.enabled = false
                effect.release()
            }
        }
        audioEffects.clear()
        hardwareAgcActive = false
    }

    private fun captureAudio(
        record: AudioRecord,
        onPcmChunk: ((ByteArray) -> Unit)?,
    ) {
        val buffer = ByteArray(4_096)
        val gain = if (hardwareAgcActive) 1f else INPUT_GAIN
        while (recorderScope.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                applyGain(buffer, read, gain)
                val chunk = buffer.copyOf(read)
                synchronized(pcmBuffer) {
                    pcmBuffer.write(chunk)
                }
                onPcmChunk?.invoke(chunk)
                continue
            }

            if (read == AudioRecord.ERROR_DEAD_OBJECT ||
                read == AudioRecord.ERROR_BAD_VALUE ||
                read == AudioRecord.ERROR_INVALID_OPERATION
            ) {
                break
            }
        }
    }

    /**
     * Applies linear gain to PCM16 little-endian samples in-place. The requested gain is reduced
     * per chunk so the loudest sample never exceeds the 16-bit range: hard-clipping distortion
     * degrades ASR accuracy far more than a slightly quieter signal. Only used when no hardware
     * AGC is attached (see [hardwareAgcActive]); it lifts quiet mic input toward the level speech
     * models expect.
     */
    private fun applyGain(buffer: ByteArray, length: Int, gain: Float) {
        if (gain <= 1f) return

        var peak = 0
        var index = 0
        while (index + 1 < length) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
            index += 2
        }
        if (peak == 0) return

        val safeGain = minOf(gain, MAX_PCM16.toFloat() / peak)
        if (safeGain <= 1f) return

        index = 0
        while (index + 1 < length) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            val amplified = (sample * safeGain).toInt().coerceIn(MIN_PCM16, MAX_PCM16)
            buffer[index] = (amplified and 0xFF).toByte()
            buffer[index + 1] = ((amplified shr 8) and 0xFF).toByte()
            index += 2
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val TAG = "AudioRecorder"

        // ~12 dB boost applied only when no hardware AGC is available. The measured input sits
        // around -35..-40 dBFS on devices without AGC; this lifts it toward ~-25 dBFS, and
        // applyGain caps the boost per chunk so peaks never clip.
        private const val INPUT_GAIN = 4f
        private const val MIN_PCM16 = -32_768
        private const val MAX_PCM16 = 32_767
    }
}
