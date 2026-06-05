package com.example.localllmvoice.data.gemma

object GemmaModelConfig {
    const val MODEL_LABEL = "Qwen2.5 1.5B Instruct"
    const val MODEL_REPOSITORY = "litert-community/Qwen2.5-1.5B-Instruct"
    const val MODEL_FILE_NAME = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    const val MODEL_URL =
        "https://huggingface.co/$MODEL_REPOSITORY/resolve/main/$MODEL_FILE_NAME"

    // LiteRT community dynamic-int8 artifact is about 1,598 MB.
    const val ESTIMATED_SIZE_BYTES = 1_598_000_000L
    const val MIN_MODEL_FILE_BYTES = 1_550_000_000L

    // Qwen2.5 1.5B Q8 peaks around 2.2 GB RSS on CPU for the 4096-context model.
    const val MIN_TOTAL_RAM_BYTES = 4L * 1024 * 1024 * 1024
    const val MIN_FREE_STORAGE_BYTES = 2_000_000_000L

    const val MAX_NUM_TOKENS = 4096 // Matches the ekv4096 LiteRT-LM artifact.
    const val CPU_NUM_THREADS = 4
    const val MAX_HISTORY_MESSAGES = 8 // Last four turns are enough for voice roleplay and reduce drift.
    const val MAX_RESPONSE_SENTENCES = 3
    const val ENABLE_SPECULATIVE_DECODING = false
    const val ENABLE_NPU_BACKEND = false

    // Qwen2.5 Instruct defaults keep answers focused while preserving natural dialogue.
    const val SAMPLER_TOP_K = 50
    const val SAMPLER_TOP_P = 0.95
    const val SAMPLER_TEMPERATURE = 0.65

    val ESTIMATED_SIZE_MB: Long
        get() = ESTIMATED_SIZE_BYTES / 1_000_000
}
