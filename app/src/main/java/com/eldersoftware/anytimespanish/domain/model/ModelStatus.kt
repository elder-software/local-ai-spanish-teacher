package com.eldersoftware.anytimespanish.domain.model

enum class ModelStatus {
    READY,
    DOWNLOAD_REQUIRED,
    DOWNLOADING,
    INITIALIZING,
    INSUFFICIENT_DEVICE,
    ERROR,
}

data class CombinedModelStatus(
    val status: ModelStatus,
    val message: String,
    val errorMessage: String? = null,
)
