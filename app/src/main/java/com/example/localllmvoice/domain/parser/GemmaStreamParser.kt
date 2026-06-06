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
     * Hides Gemma control markers (think blocks, channel/turn headers) from user-visible output.
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
                val controlMarker = findEarliestControlMarker()
                if (controlMarker != null) {
                    val visible = pendingBuffer.substring(0, controlMarker.startIndex)
                    if (visible.isNotEmpty()) {
                        onUserVisibleText(visible)
                    }
                    pendingBuffer.delete(0, controlMarker.endIndexExclusive)

                    if (controlMarker.kind == ControlMarkerKind.THINK_BLOCK) {
                        isInsideThinkBlock = true
                    }
                } else {
                    val retainLength = longestControlPrefixRetainLength()
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

    private fun findEarliestControlMarker(): ControlMarker? =
        CONTROL_MARKERS
            .mapNotNull { marker ->
                val startIndex = pendingBuffer.indexOf(marker.token)
                if (startIndex < 0) {
                    null
                } else {
                    val suffixLength = when (marker.kind) {
                        ControlMarkerKind.THINK_BLOCK -> 0
                        ControlMarkerKind.CHANNEL_HEADER ->
                            pendingBuffer.knownNameLengthAt(
                                startIndex = startIndex + marker.token.length,
                                knownNames = CHANNEL_NAMES,
                            )
                        ControlMarkerKind.TURN_HEADER ->
                            pendingBuffer.knownNameLengthAt(
                                startIndex = startIndex + marker.token.length,
                                knownNames = TURN_NAMES,
                            )
                    }
                    ControlMarker(
                        kind = marker.kind,
                        startIndex = startIndex,
                        endIndexExclusive = startIndex + marker.token.length + suffixLength,
                    )
                }
            }
            .minByOrNull { it.startIndex }

    private fun StringBuilder.knownNameLengthAt(
        startIndex: Int,
        knownNames: List<String>,
    ): Int {
        if (startIndex >= length) {
            return trailingWhitespaceLengthAt(startIndex)
        }

        val matchedName = knownNames.firstOrNull { startsWithAt(startIndex, it) }
        return if (matchedName != null) {
            matchedName.length + trailingWhitespaceLengthAt(startIndex + matchedName.length)
        } else {
            trailingWhitespaceLengthAt(startIndex)
        }
    }

    private fun StringBuilder.trailingWhitespaceLengthAt(startIndex: Int): Int {
        var whitespaceLength = 0
        while (startIndex + whitespaceLength < length) {
            val char = this[startIndex + whitespaceLength]
            if (!char.isWhitespace()) {
                break
            }
            whitespaceLength += 1
        }
        return whitespaceLength
    }

    private fun StringBuilder.startsWithAt(startIndex: Int, value: String): Boolean {
        if (startIndex + value.length > length) {
            return false
        }
        return substring(startIndex, startIndex + value.length) == value
    }

    private fun longestControlPrefixRetainLength(): Int {
        val prefixes = buildList {
            add(THINK_START)
            add(THINK_END)
            CONTROL_MARKERS.forEach { add(it.token) }
        }
        return prefixes.maxOf { pendingBuffer.longestSuffixMatchingPrefixOf(it) }
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

    private enum class ControlMarkerKind {
        THINK_BLOCK,
        CHANNEL_HEADER,
        TURN_HEADER,
    }

    private data class ControlMarker(
        val kind: ControlMarkerKind,
        val startIndex: Int,
        val endIndexExclusive: Int,
    )

    private data class ControlMarkerPattern(
        val kind: ControlMarkerKind,
        val token: String,
    )

    companion object {
        private const val THINK_START = "<|think|>"
        private const val THINK_END = "</|think|>"

        private val CONTROL_MARKERS = listOf(
            ControlMarkerPattern(ControlMarkerKind.THINK_BLOCK, THINK_START),
            ControlMarkerPattern(ControlMarkerKind.CHANNEL_HEADER, "<|channel|>"),
            ControlMarkerPattern(ControlMarkerKind.CHANNEL_HEADER, "<channel|>"),
            ControlMarkerPattern(ControlMarkerKind.TURN_HEADER, "<|turn|>"),
            ControlMarkerPattern(ControlMarkerKind.TURN_HEADER, "<turn|>"),
        )

        private val CHANNEL_NAMES = listOf("final", "analysis", "commentary")
        private val TURN_NAMES = listOf("model", "user")
    }
}
