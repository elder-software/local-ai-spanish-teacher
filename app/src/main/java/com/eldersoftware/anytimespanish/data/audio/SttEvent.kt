package com.eldersoftware.anytimespanish.data.audio

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Failure(val message: String) : SttEvent

    /**
     * Live microphone signal level for debugging/visualisation.
     * [rms] is normalised to 0f..1f, where 0f is silence and 1f is full-scale PCM16.
     */
    data class AudioLevel(val rms: Float) : SttEvent
}
