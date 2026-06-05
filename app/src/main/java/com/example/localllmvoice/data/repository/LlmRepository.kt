package com.example.localllmvoice.data.repository

import com.example.localllmvoice.data.gemma.DeviceCapability
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
    val isDemoMode: Boolean

    fun checkModelAvailability(): Flow<ModelAvailability>

    fun downloadModel(): Flow<ModelDownloadEvent>

    fun generateStreamingResponse(
        systemPrompt: String,
        conversationContext: String,
        userText: String,
    ): Flow<String>

    suspend fun resetConversation()
}
