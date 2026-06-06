package com.example.localllmvoice.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
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
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext Result.failure(IllegalStateException("AudioRecord failed to initialize"))
        }

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

    suspend fun stopAndGetRawPcm(): Result<ByteArray> = withContext(Dispatchers.IO) {
        val wasRecording = recorderMutex.withLock { audioRecord != null }
        if (!wasRecording) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }
        stopActiveRecording(clearBuffer = false)
        val pcm = synchronized(pcmBuffer) {
            val trimmedPcm = Pcm16AudioTrimmer.trim(pcmBuffer.toByteArray())
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
        record.release()

        if (clearBuffer) {
            synchronized(pcmBuffer) { pcmBuffer.reset() }
        }
    }

    private fun captureAudio(
        record: AudioRecord,
        onPcmChunk: ((ByteArray) -> Unit)?,
    ) {
        val buffer = ByteArray(4_096)
        while (recorderScope.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
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

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
