package com.example.localllmvoice.domain.parser

import java.text.Normalizer

object RepetitionDetector {
    /**
     * Checks if the given text contains degenerate repetition loops.
     * Returns true if a repetition loop is detected.
     */
    fun isRepetitive(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 15) return false

        // 1. Check for consecutive sentence repetition
        // Split by sentence delimiters: '.', '?', '!'
        val sentences = trimmed.split(Regex("[.?!]+"))
            .map { it.trim() }
            .filter { it.length > 10 } // Only check reasonably long sentences to avoid false positives

        for (i in 0 until sentences.size - 1) {
            if (sentences[i].equals(sentences[i + 1], ignoreCase = true)) {
                return true
            }
        }

        // 2. Check for consecutive phrase repetition (e.g., repeating a phrase of 3+ words consecutively)
        val words = trimmed.split(Regex("\\s+"))
            .map { it.lowercase().filter { char -> char.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

        if (words.size >= 6) {
            val maxWindowSize = minOf(10, words.size / 2)
            for (windowSize in 3..maxWindowSize) {
                for (i in 0..words.size - 2 * windowSize) {
                    val firstPhrase = words.subList(i, i + windowSize)
                    val secondPhrase = words.subList(i + windowSize, i + 2 * windowSize)
                    if (firstPhrase == secondPhrase) {
                        return true
                    }
                }
            }
        }

        return false
    }

    fun isLowValueAcknowledgement(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        if (text.contains("?") || text.contains("¿")) return false

        val words = normalized.split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (words.size > MAX_ACKNOWLEDGEMENT_WORDS) return false

        val phrase = words.joinToString(" ")
        return phrase in LOW_VALUE_ACKNOWLEDGEMENTS ||
            words.all { word -> word in LOW_VALUE_ACKNOWLEDGEMENT_WORDS }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .trim()

    private const val MAX_ACKNOWLEDGEMENT_WORDS = 8

    private val LOW_VALUE_ACKNOWLEDGEMENTS = listOf(
        "perfecto",
        "perfecto y que bien",
        "que bien",
        "muy bien",
        "excelente",
        "genial",
        "bien hecho",
        "estupendo",
        "vale",
        "claro",
        "de acuerdo",
    )

    private val LOW_VALUE_ACKNOWLEDGEMENT_WORDS = setOf(
        "perfecto",
        "y",
        "que",
        "bien",
        "muy",
        "excelente",
        "genial",
        "hecho",
        "estupendo",
        "vale",
        "claro",
        "de",
        "acuerdo",
    )
}
