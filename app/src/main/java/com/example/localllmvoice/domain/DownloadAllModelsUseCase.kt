package com.example.localllmvoice.domain

import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.data.repository.ModelDownloadEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

sealed interface DownloadAllModelsEvent {
    data class GemmaProgress(val downloadedBytes: Long, val totalBytes: Long) : DownloadAllModelsEvent
    data class SttProgress(val progressPercent: Int) : DownloadAllModelsEvent
    data object Completed : DownloadAllModelsEvent
    data class Failed(val message: String) : DownloadAllModelsEvent
}

class DownloadAllModelsUseCase(
    private val gemmaLlmRepository: GemmaLlmRepository,
    private val speechToTextRepository: SpeechToTextEngine
) {
    operator fun invoke(): Flow<DownloadAllModelsEvent> = flow {
        try {
            val availability = gemmaLlmRepository.checkModelAvailability().first()
            val gemmaNeedsDownload = availability.status == GemmaModelStatus.DOWNLOAD_REQUIRED || 
                    availability.status == GemmaModelStatus.ERROR

            if (gemmaNeedsDownload) {
                var gemmaFailed = false
                var failureMessage = ""
                gemmaLlmRepository.downloadModel().collect { event ->
                    when (event) {
                        is ModelDownloadEvent.Progress -> {
                            emit(DownloadAllModelsEvent.GemmaProgress(event.downloadedBytes, event.totalBytes))
                        }
                        is ModelDownloadEvent.Failed -> {
                            gemmaFailed = true
                            failureMessage = event.message
                        }
                        ModelDownloadEvent.Completed -> {
                            // Proceed to STT model download
                        }
                    }
                }
                if (gemmaFailed) {
                    emit(DownloadAllModelsEvent.Failed("Gemma download failed: $failureMessage"))
                    return@flow
                }
            }

            val sttNeedsDownload = !speechToTextRepository.isModelReady()
            if (sttNeedsDownload) {
                try {
                    speechToTextRepository.downloadModel().collect { progress ->
                        emit(DownloadAllModelsEvent.SttProgress(progress))
                    }
                } catch (e: Exception) {
                    emit(DownloadAllModelsEvent.Failed("STT download failed: ${e.message}"))
                    return@flow
                }
            }

            emit(DownloadAllModelsEvent.Completed)
        } catch (e: Exception) {
            emit(DownloadAllModelsEvent.Failed("Download failed: ${e.message}"))
        }
    }
}
