package com.example.localllmvoice.domain

import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.onboarding.OnboardingPreferences
import com.example.localllmvoice.data.repository.GemmaLlmRepository
import com.example.localllmvoice.data.repository.GemmaModelStatus

sealed interface StartupState {
    data object NeedsOnboarding : StartupState
    data object NeedsModelDownload : StartupState
    data object ReadyForDashboard : StartupState
}

class DecideStartupStateUseCase(
    private val onboardingPreferences: OnboardingPreferences,
    private val gemmaLlmRepository: GemmaLlmRepository,
    private val speechToTextEngine: SpeechToTextEngine,
) {
    operator fun invoke(): StartupState {
        if (!onboardingPreferences.isComplete()) {
            return StartupState.NeedsOnboarding
        }

        return try {
            val sttReady = speechToTextEngine.isModelReady()
            when (gemmaLlmRepository.checkDownloadedModelAvailability().status) {
                GemmaModelStatus.READY -> {
                    if (sttReady) {
                        StartupState.ReadyForDashboard
                    } else {
                        StartupState.NeedsModelDownload
                    }
                }

                GemmaModelStatus.DOWNLOAD_REQUIRED,
                GemmaModelStatus.ERROR -> {
                    StartupState.NeedsModelDownload
                }

                GemmaModelStatus.DOWNLOADING,
                GemmaModelStatus.INITIALIZING,
                GemmaModelStatus.INSUFFICIENT_DEVICE -> {
                    StartupState.ReadyForDashboard
                }
            }
        } catch (_: Exception) {
            StartupState.NeedsModelDownload
        }
    }
}
