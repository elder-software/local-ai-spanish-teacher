package com.eldersoftware.anytimespanish.data.audio

object Pcm16AudioTrimmer {
    const val DEFAULT_MAX_DURATION_MS = 8_000

    private const val BYTES_PER_SAMPLE = 2
    private const val SAMPLE_RATE = 16_000
    private const val FRAME_MS = 20
    private const val PADDING_MS = 200
    private const val FALLBACK_DURATION_MS = 600
    private const val SPEECH_RMS_THRESHOLD = 600

    private const val FRAME_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * FRAME_MS / 1_000
    private const val PADDING_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * PADDING_MS / 1_000
    private const val FALLBACK_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * FALLBACK_DURATION_MS / 1_000
    private const val SPEECH_RMS_THRESHOLD_SQUARED = SPEECH_RMS_THRESHOLD * SPEECH_RMS_THRESHOLD

    fun trim(pcmData: ByteArray, maxDurationMs: Int = DEFAULT_MAX_DURATION_MS): ByteArray {
        val maxBytes = bytesForDuration(maxDurationMs)
        val usableSize = pcmData.size - (pcmData.size % BYTES_PER_SAMPLE)
        if (usableSize <= 0) return ByteArray(0)

        var firstSpeechFrame = -1
        var lastSpeechFrame = -1

        var frameStart = 0
        while (frameStart < usableSize) {
            val frameEnd = minOf(frameStart + FRAME_BYTES, usableSize)
            if (isSpeechFrame(pcmData, frameStart, frameEnd)) {
                if (firstSpeechFrame < 0) {
                    firstSpeechFrame = frameStart
                }
                lastSpeechFrame = frameEnd
            }
            frameStart += FRAME_BYTES
        }

        if (firstSpeechFrame < 0) {
            return pcmData.copyOfRange(0, minOf(usableSize, FALLBACK_BYTES))
        }

        val start = alignToSampleBoundary(maxOf(0, firstSpeechFrame - PADDING_BYTES))
        val speechEnd = alignToSampleBoundary(minOf(usableSize, lastSpeechFrame + PADDING_BYTES))
        val end = minOf(usableSize, start + maxBytes, speechEnd)

        return pcmData.copyOfRange(start, end)
    }

    private fun bytesForDuration(durationMs: Int): Int {
        val bytes = durationMs.toLong() * SAMPLE_RATE * BYTES_PER_SAMPLE / 1_000
        return alignToSampleBoundary(bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun isSpeechFrame(pcmData: ByteArray, start: Int, end: Int): Boolean {
        var sumSquares = 0L
        var sampleCount = 0
        var index = start
        while (index + 1 < end) {
            val low = pcmData[index].toInt() and 0xFF
            val high = pcmData[index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            sumSquares += sample.toLong() * sample.toLong()
            sampleCount++
            index += BYTES_PER_SAMPLE
        }

        if (sampleCount == 0) return false
        return sumSquares / sampleCount >= SPEECH_RMS_THRESHOLD_SQUARED
    }

    private fun alignToSampleBoundary(value: Int): Int =
        value - (value % BYTES_PER_SAMPLE)
}
