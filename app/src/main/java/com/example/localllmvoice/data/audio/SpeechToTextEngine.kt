package com.example.localllmvoice.data.audio

import kotlinx.coroutines.flow.Flow

interface SpeechToTextEngine {
    fun hasRecordPermission(): Boolean

    fun isAvailable(): Boolean

    fun transcribe(languageTag: String): Flow<SttEvent>

    fun stopListening()
}
