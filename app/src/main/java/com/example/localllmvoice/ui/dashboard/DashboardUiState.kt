package com.example.localllmvoice.ui.dashboard

import com.example.localllmvoice.data.gemma.DeviceCapability
import com.example.localllmvoice.data.repository.GemmaModelStatus
import com.example.localllmvoice.domain.model.ConversationTopic

data class DashboardUiState(
    val topics: List<ConversationTopic> = emptyList(),
    val modelStatus: GemmaModelStatus = GemmaModelStatus.DOWNLOAD_REQUIRED,
    val modelStatusMessage: String = "",
    val activeBackend: String? = null,
    val deviceCapability: DeviceCapability? = null,
    val isDownloading: Boolean = false,
    val downloadProgressBytes: Long = 0L,
    val downloadTotalBytes: Long = 0L,
    val errorMessage: String? = null,
) {
    val canStartConversation: Boolean = modelStatus == GemmaModelStatus.READY

    val canDownload: Boolean = modelStatus == GemmaModelStatus.DOWNLOAD_REQUIRED || modelStatus == GemmaModelStatus.ERROR
}
