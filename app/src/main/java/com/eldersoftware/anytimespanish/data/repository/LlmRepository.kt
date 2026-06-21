package com.eldersoftware.anytimespanish.data.repository

import com.eldersoftware.anytimespanish.data.gemma.DeviceCapability
import kotlinx.coroutines.flow.Flow

enum class GemmaModelStatus {
    READY,
    DOWNLOAD_REQUIRED,
    DOWNLOADING,
    INITIALIZING,
    INSUFFICIENT_DEVICE,
    ERROR,
}

data class ModelAvailability(
    val status: GemmaModelStatus,
    val message: String,
    val activeBackend: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val deviceCapability: DeviceCapability? = null,
)

sealed interface ModelDownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadEvent
    data object Completed : ModelDownloadEvent
    data class Failed(val message: String) : ModelDownloadEvent
}

interface LlmRepository {
    fun checkModelAvailability(): Flow<ModelAvailability>

    fun downloadModel(): Flow<ModelDownloadEvent>

    fun generateStreamingResponse(
        systemPrompt: String,
        conversationContext: String,
        userText: String,
    ): Flow<String>

    fun analyzeConversation(transcript: String): Flow<String>

    suspend fun punctuateTranscript(transcript: String): String

    /**
     * Corrects obvious speech-recognition errors in [transcript] (e.g. non-words produced by
     * the small ASR model, such as "quidando" → "quedando") and adds punctuation, using the
     * [topicTitle] and [conversationContext] to disambiguate. Intended learner errors in grammar
     * or vocabulary are preserved — only machine-transcription artifacts are fixed.
     */
    suspend fun correctTranscript(
        transcript: String,
        topicTitle: String,
        conversationContext: String,
    ): String

    suspend fun generateNextReplySuggestion(
        topicTitle: String,
        conversationContext: String,
        avoidSuggestions: List<String> = emptyList(),
    ): String

    suspend fun translateText(text: String, targetLanguage: String): String

    suspend fun resetConversation()
}
