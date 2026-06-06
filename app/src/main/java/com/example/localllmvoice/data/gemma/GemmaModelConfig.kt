package com.example.localllmvoice.data.gemma

object GemmaModelConfig {
    const val MODEL_LABEL = "Gemma 4 E2B IT"
    const val MODEL_REPOSITORY = "litert-community/gemma-4-E2B-it-litert-lm"
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_URL =
        "https://huggingface.co/$MODEL_REPOSITORY/resolve/main/$MODEL_FILE_NAME"

    // The default LiteRT-LM artifact is 2,588,147,712 bytes (~2,588 MB) on disk.
    const val ESTIMATED_SIZE_BYTES = 2_588_147_712L
    const val MIN_MODEL_FILE_BYTES = 2_550_000_000L

    // Gemma 4 E2B on Android reports about 1.7 GB CPU / 0.7 GB GPU peak footprint.
    const val MIN_TOTAL_RAM_BYTES = 6L * 1024 * 1024 * 1024
    const val MIN_FREE_STORAGE_BYTES = 3_200_000_000L

    const val MAX_NUM_TOKENS = 2048 // Default benchmarked context window for this artifact.
    const val CPU_NUM_THREADS = 4
    const val MAX_HISTORY_MESSAGES = 8 // Last four turns are enough for voice roleplay and reduce drift.
    const val MAX_RESPONSE_SENTENCES = 3
    const val ENABLE_SPECULATIVE_DECODING = false
    const val ENABLE_NPU_BACKEND = false

    // Gemma 4 recommended sampling defaults.
    const val SAMPLER_TOP_K = 64
    const val SAMPLER_TOP_P = 0.95
    const val SAMPLER_TEMPERATURE = 1.0

    val ESTIMATED_SIZE_MB: Long
        get() = ESTIMATED_SIZE_BYTES / 1_000_000
}
