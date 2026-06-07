package com.example.localllmvoice.domain

import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.data.repository.ModelDownloadEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

sealed interface DownloadAllModelsEvent {
    data class Progress(val progressPercent: Int, val currentDownload: CurrentDownload) : DownloadAllModelsEvent
    data object Completed : DownloadAllModelsEvent
    data class Failed(val message: String) : DownloadAllModelsEvent
}

enum class CurrentDownload {
    Gemma, STT
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
            val sttNeedsDownload = !speechToTextRepository.isModelReady()

            val totalSteps = (if (gemmaNeedsDownload) 1 else 0) + (if (sttNeedsDownload) 1 else 0)

            if (totalSteps == 0) {
                emit(DownloadAllModelsEvent.Completed)
                return@flow
            }

            // Proportional weighting
            val gemmaWeight = if (gemmaNeedsDownload && sttNeedsDownload) 0.90f else if (gemmaNeedsDownload) 1.0f else 0.0f
            val sttWeight = if (gemmaNeedsDownload && sttNeedsDownload) 0.10f else if (sttNeedsDownload) 1.0f else 0.0f

            var currentBaseProgress = 0.0f

            if (gemmaNeedsDownload) {
                var gemmaFailed = false
                var failureMessage = ""
                gemmaLlmRepository.downloadModel().collect { event ->
                    when (event) {
                        is ModelDownloadEvent.Progress -> {
                            val gemmaPercent = if (event.totalBytes > 0) {
                                event.downloadedBytes.toFloat() / event.totalBytes.toFloat()
                            } else {
                                0.0f
                            }
                            val overallPercent = (currentBaseProgress + (gemmaPercent * gemmaWeight)) * 100
                            val downloadedMb = event.downloadedBytes / 1_000_000
                            val totalMb = event.totalBytes / 1_000_000
                            emit(
                                DownloadAllModelsEvent.Progress(
                                    progressPercent = overallPercent.toInt().coerceIn(0, 100),
                                    currentDownload = CurrentDownload.Gemma
                                )
                            )
                        }
                        is ModelDownloadEvent.Failed -> {
                            gemmaFailed = true
                            failureMessage = event.message
                        }
                        ModelDownloadEvent.Completed -> {
                            // Proceed
                        }
                    }
                }
                if (gemmaFailed) {
                    emit(DownloadAllModelsEvent.Failed("Gemma download failed: $failureMessage"))
                    return@flow
                }
                currentBaseProgress += gemmaWeight
            }

            if (sttNeedsDownload) {
                try {
                    speechToTextRepository.downloadModel().collect { progressPercent ->
                        val overallPercent = (currentBaseProgress + ((progressPercent.toFloat() / 100f) * sttWeight)) * 100
                        emit(
                            DownloadAllModelsEvent.Progress(
                                progressPercent = overallPercent.toInt().coerceIn(0, 100),
                                currentDownload = CurrentDownload.STT
                            )
                        )
                    }
                } catch (e: Exception) {
                    emit(DownloadAllModelsEvent.Failed("STT download failed: ${e.message}"))
                    return@flow
                }
                currentBaseProgress += sttWeight
            }

            emit(DownloadAllModelsEvent.Completed)
        } catch (e: Exception) {
            emit(DownloadAllModelsEvent.Failed("Download failed: ${e.message}"))
        }
    }
}
