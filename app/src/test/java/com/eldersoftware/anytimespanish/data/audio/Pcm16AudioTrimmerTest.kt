package com.eldersoftware.anytimespanish.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcm16AudioTrimmerTest {
    @Test
    fun trim_removesLeadingAndTrailingSilence() {
        val audio = pcm16(
            silenceSamples = SAMPLE_RATE,
            speechSamples = SAMPLE_RATE,
            trailingSilenceSamples = SAMPLE_RATE,
        )

        val trimmed = Pcm16AudioTrimmer.trim(audio)

        assertTrue(trimmed.size < audio.size)
        assertTrue(trimmed.size > SAMPLE_RATE * BYTES_PER_SAMPLE)
        assertSampleEquals(2_000, trimmed, firstSpeechOffset(trimmed))
    }

    @Test
    fun trim_capsLongRecordings() {
        val audio = pcm16(
            silenceSamples = 0,
            speechSamples = SAMPLE_RATE * 12,
            trailingSilenceSamples = 0,
        )

        val trimmed = Pcm16AudioTrimmer.trim(audio)

        assertEquals(SAMPLE_RATE * BYTES_PER_SAMPLE * 8, trimmed.size)
    }

    @Test
    fun trim_keepsShortFallbackForSilentAudio() {
        val audio = ByteArray(SAMPLE_RATE * BYTES_PER_SAMPLE * 2)

        val trimmed = Pcm16AudioTrimmer.trim(audio)

        assertEquals(SAMPLE_RATE * BYTES_PER_SAMPLE * 600 / 1_000, trimmed.size)
    }

    private fun pcm16(
        silenceSamples: Int,
        speechSamples: Int,
        trailingSilenceSamples: Int,
    ): ByteArray {
        val samples = IntArray(silenceSamples + speechSamples + trailingSilenceSamples)
        for (index in silenceSamples until silenceSamples + speechSamples) {
            samples[index] = 2_000
        }
        return samples.toPcm16Bytes()
    }

    private fun IntArray.toPcm16Bytes(): ByteArray {
        val bytes = ByteArray(size * BYTES_PER_SAMPLE)
        forEachIndexed { index, sample ->
            bytes[index * BYTES_PER_SAMPLE] = (sample and 0xFF).toByte()
            bytes[index * BYTES_PER_SAMPLE + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun firstSpeechOffset(bytes: ByteArray): Int {
        var index = 0
        while (index + 1 < bytes.size) {
            val low = bytes[index].toInt() and 0xFF
            val high = bytes[index + 1].toInt()
            val sample = (low or (high shl 8)).toShort().toInt()
            if (sample != 0) return index
            index += BYTES_PER_SAMPLE
        }
        return 0
    }

    private fun assertSampleEquals(expected: Int, bytes: ByteArray, offset: Int) {
        val low = bytes[offset].toInt() and 0xFF
        val high = bytes[offset + 1].toInt()
        val sample = (low or (high shl 8)).toShort().toInt()
        assertEquals(expected, sample)
    }

    private companion object {
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
    }
}
