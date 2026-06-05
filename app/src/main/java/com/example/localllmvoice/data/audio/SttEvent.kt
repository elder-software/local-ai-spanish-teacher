package com.example.localllmvoice.data.audio

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Failure(val message: String) : SttEvent
}
