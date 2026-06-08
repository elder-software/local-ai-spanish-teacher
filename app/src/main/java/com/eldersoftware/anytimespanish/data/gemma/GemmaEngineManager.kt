package com.eldersoftware.anytimespanish.data.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalApi::class)
class GemmaEngineManager(
    private val context: Context,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var lastInitError: String? = null
    var activeBackendLabel: String = "CPU"
        private set

    suspend fun initialize(modelPath: String) {
        mutex.withLock {
            if (engine != null) return
            withContext(Dispatchers.Default) {
                val cacheDir = context.cacheDir.absolutePath
                val npuBackend = if (GemmaModelConfig.ENABLE_NPU_BACKEND) {
                    Backend.NPU(
                        nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                    )
                } else {
                    null
                }
                engine = if (GemmaModelConfig.ENABLE_SPECULATIVE_DECODING) {
                    tryCreateEngine(
                        modelPath,
                        Backend.GPU(),
                        cacheDir,
                        enableSpeculativeDecoding = true,
                    )
                } else {
                    null
                }
                    ?: tryCreateEngine(modelPath, Backend.GPU(), cacheDir, enableSpeculativeDecoding = false)
                    ?: npuBackend?.let {
                        tryCreateEngine(modelPath, it, cacheDir, enableSpeculativeDecoding = false)
                    }
                    ?: tryCreateEngine(
                        modelPath,
                        Backend.CPU(numOfThreads = GemmaModelConfig.CPU_NUM_THREADS),
                        cacheDir,
                        enableSpeculativeDecoding = false,
                    )
                    ?: throw IllegalStateException(
                        "Could not initialize LLM engine: ${lastInitError ?: "unknown error"}",
                    )
            }
        }
    }

    suspend fun createConversation(
        systemPrompt: String,
        initialMessages: List<Message> = emptyList(),
        samplerConfig: SamplerConfig = SamplerConfig(
            topK = GemmaModelConfig.SAMPLER_TOP_K,
            topP = GemmaModelConfig.SAMPLER_TOP_P,
            temperature = GemmaModelConfig.SAMPLER_TEMPERATURE,
        ),
    ): Conversation {
        val activeEngine = mutex.withLock {
            engine ?: throw IllegalStateException("Engine not initialized")
        }
        return activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = initialMessages,
                samplerConfig = samplerConfig,
                automaticToolCalling = false,
                channels = emptyList(),
            ),
        )
    }

    suspend fun shutdown() {
        mutex.withLock {
            runCatching { engine?.close() }
            engine = null
        }
    }

    private fun tryCreateEngine(
        modelPath: String,
        backend: Backend,
        cacheDir: String,
        enableSpeculativeDecoding: Boolean,
    ): Engine? {
        val modelFile = java.io.File(modelPath)
        if (!modelFile.isFile || !modelPath.endsWith(".litertlm", ignoreCase = true)) {
            Log.e(TAG, "Refusing to load non-file model path: $modelPath")
            return null
        }
        return try {
            ExperimentalFlags.enableSpeculativeDecoding =
                if (enableSpeculativeDecoding) true else null
            val config = EngineConfig(
                modelPath = modelFile.canonicalPath,
                backend = backend,
                maxNumTokens = GemmaModelConfig.MAX_NUM_TOKENS,
                cacheDir = cacheDir,
            )
            val created = Engine(config)
            created.initialize()
            activeBackendLabel = backendLabel(backend)
            Log.i(
                TAG,
                "LLM engine ready on $activeBackendLabel " +
                    "(maxTokens=${GemmaModelConfig.MAX_NUM_TOKENS}, speculative=$enableSpeculativeDecoding)",
            )
            created
        } catch (e: Exception) {
            lastInitError = "${backendLabel(backend)}: ${e.message}"
            Log.w(TAG, "Backend ${backendLabel(backend)} failed: ${e.message}", e)
            null
        } finally {
            ExperimentalFlags.enableSpeculativeDecoding = null
        }
    }

    private fun backendLabel(backend: Backend): String =
        when (backend) {
            is Backend.CPU -> "CPU"
            is Backend.GPU -> "GPU"
            is Backend.NPU -> "NPU"
        }

    companion object {
        private const val TAG = "GemmaEngineManager"
    }
}
