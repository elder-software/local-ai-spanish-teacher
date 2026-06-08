package com.eldersoftware.anytimespanish.domain

import com.eldersoftware.anytimespanish.data.audio.SpeechToTextEngine
import com.eldersoftware.anytimespanish.data.gemma.GemmaModelConfig
import com.eldersoftware.anytimespanish.data.repository.GemmaLlmRepository
import com.eldersoftware.anytimespanish.data.repository.GemmaModelStatus
import com.eldersoftware.anytimespanish.domain.model.CombinedModelStatus
import com.eldersoftware.anytimespanish.domain.model.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveModelStatusUseCase(
    private val gemmaLlmRepository: GemmaLlmRepository,
    private val speechToTextEngine: SpeechToTextEngine,
) {
    operator fun invoke(): Flow<CombinedModelStatus> {
        return gemmaLlmRepository.checkModelAvailability().map { availability ->
            val sttReady = speechToTextEngine.isModelReady()
            val mappedStatus = when (availability.status) {
                GemmaModelStatus.READY -> ModelStatus.READY
                GemmaModelStatus.DOWNLOAD_REQUIRED -> ModelStatus.DOWNLOAD_REQUIRED
                GemmaModelStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
                GemmaModelStatus.INITIALIZING -> ModelStatus.INITIALIZING
                GemmaModelStatus.INSUFFICIENT_DEVICE -> ModelStatus.INSUFFICIENT_DEVICE
                GemmaModelStatus.ERROR -> ModelStatus.ERROR
            }

            if (!sttReady && mappedStatus == ModelStatus.READY) {
                CombinedModelStatus(
                    status = ModelStatus.DOWNLOAD_REQUIRED,
                    message = "Download Spanish STT model to run offline",
                    errorMessage = null,
                )
            } else if (!sttReady && mappedStatus == ModelStatus.DOWNLOAD_REQUIRED) {
                CombinedModelStatus(
                    status = ModelStatus.DOWNLOAD_REQUIRED,
                    message = "Download Gemma and Spanish STT models (~${GemmaModelConfig.ESTIMATED_SIZE_MB} MB) to run offline",
                    errorMessage = null,
                )
            } else {
                CombinedModelStatus(
                    status = mappedStatus,
                    message = availability.message,
                    errorMessage = if (mappedStatus == ModelStatus.ERROR) availability.message else null,
                )
            }
        }
    }
}
