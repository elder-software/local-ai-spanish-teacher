package com.example.localllmvoice.domain.parser

class GemmaStreamParser {
    private var isInsideThinkBlock = false
    private val thoughtBuffer = StringBuilder()
    private val pendingBuffer = StringBuilder()

    fun reset() {
        isInsideThinkBlock = false
        thoughtBuffer.setLength(0)
        pendingBuffer.setLength(0)
    }

    /**
     * Parses streaming tokens sequentially.
     * Extracts text inside <|think|> tags without leaking them to the user or TTS.
     */
    fun processToken(token: String, onUserVisibleText: (String) -> Unit): String? {
        pendingBuffer.append(token)
        var completedThought: String? = null

        while (pendingBuffer.isNotEmpty()) {
            if (isInsideThinkBlock) {
                val endIndex = pendingBuffer.indexOf(THINK_END)
                if (endIndex >= 0) {
                    thoughtBuffer.append(pendingBuffer.substring(0, endIndex))
                    pendingBuffer.delete(0, endIndex + THINK_END.length)
                    completedThought = thoughtBuffer.toString()
                    thoughtBuffer.setLength(0)
                    isInsideThinkBlock = false
                } else {
                    val retainLength = pendingBuffer.longestSuffixMatchingPrefixOf(THINK_END)
                    val appendLength = pendingBuffer.length - retainLength
                    if (appendLength > 0) {
                        thoughtBuffer.append(pendingBuffer.substring(0, appendLength))
                        pendingBuffer.delete(0, appendLength)
                    }
                    break
                }
            } else {
                val startIndex = pendingBuffer.indexOf(THINK_START)
                if (startIndex >= 0) {
                    val visible = pendingBuffer.substring(0, startIndex)
                    if (visible.isNotEmpty()) {
                        onUserVisibleText(visible)
                    }
                    pendingBuffer.delete(0, startIndex + THINK_START.length)
                    isInsideThinkBlock = true
                } else {
                    val retainLength = pendingBuffer.longestSuffixMatchingPrefixOf(THINK_START)
                    val visibleLength = pendingBuffer.length - retainLength
                    if (visibleLength > 0) {
                        onUserVisibleText(pendingBuffer.substring(0, visibleLength))
                        pendingBuffer.delete(0, visibleLength)
                    }
                    break
                }
            }
        }

        return completedThought
    }

    private fun StringBuilder.longestSuffixMatchingPrefixOf(value: String): Int {
        val maxLength = minOf(length, value.length - 1)
        for (candidateLength in maxLength downTo 1) {
            if (endsWith(value.substring(0, candidateLength))) {
                return candidateLength
            }
        }
        return 0
    }

    private fun StringBuilder.indexOf(value: String): Int =
        toString().indexOf(value)

    private fun StringBuilder.endsWith(value: String): Boolean =
        length >= value.length && substring(length - value.length, length) == value

    companion object {
        private const val THINK_START = "<|think|>"
        private const val THINK_END = "</|think|>"
    }
}
