package com.example.localllmvoice.data.repository

import android.content.Context
import android.util.Log
import com.example.localllmvoice.data.gemma.DeviceCapabilityChecker
import com.example.localllmvoice.data.gemma.GemmaEngineManager
import com.example.localllmvoice.data.gemma.GemmaModelConfig
import com.example.localllmvoice.data.gemma.GemmaModelDownloader
import com.example.localllmvoice.data.gemma.GemmaModelStore
import com.example.localllmvoice.domain.parser.RepetitionDetector
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalApi::class)
class GemmaLlmRepository(
    context: Context,
) : LlmRepository {
    private val modelStore = GemmaModelStore(context)
    private val downloader = GemmaModelDownloader(modelStore)
    private val capabilityChecker = DeviceCapabilityChecker(context, modelStore)
    private val engineManager = GemmaEngineManager(context)
    private val conversationMutex = Mutex()
    private var activeConversation: Conversation? = null

    override val isDemoMode: Boolean = false

    override fun checkModelAvailability(): Flow<ModelAvailability> = flow {
        modelStore.ensureModelDirectory()
        val capability = capabilityChecker.evaluate()

        if (!capability.canRunConfiguredModel) {
            emit(
                ModelAvailability(
                    status = GemmaModelStatus.INSUFFICIENT_DEVICE,
                    message = "Device below recommended specs for ${GemmaModelConfig.MODEL_LABEL}",
                    deviceCapability = capability,
                ),
            )
            return@flow
        }

        if (!modelStore.isModelReady()) {
            emit(
                ModelAvailability(
                    status = GemmaModelStatus.DOWNLOAD_REQUIRED,
                    message = "Download ${GemmaModelConfig.MODEL_LABEL} (~${GemmaModelConfig.ESTIMATED_SIZE_MB} MB) to run offline",
                    deviceCapability = capability,
                    totalBytes = GemmaModelConfig.ESTIMATED_SIZE_BYTES,
                ),
            )
            return@flow
        }

        emit(
            ModelAvailability(
                status = GemmaModelStatus.INITIALIZING,
                message = "Loading ${GemmaModelConfig.MODEL_LABEL} into memory…",
                deviceCapability = capability,
            ),
        )

        try {
            val modelPath = modelStore.resolveEngineModelPath().getOrThrow()
            engineManager.initialize(modelPath)
            emit(
                ModelAvailability(
                    status = GemmaModelStatus.READY,
                    message = "${GemmaModelConfig.MODEL_LABEL} ready (${engineManager.activeBackendLabel})",
                    activeBackend = engineManager.activeBackendLabel,
                    deviceCapability = capability,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Engine init failed", e)
            emit(
                ModelAvailability(
                    status = GemmaModelStatus.ERROR,
                    message = e.message ?: "Failed to load model",
                    deviceCapability = capability,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadModel(): Flow<ModelDownloadEvent> = flow {
        try {
            downloader.download().collect { progress ->
                emit(ModelDownloadEvent.Progress(progress.downloadedBytes, progress.totalBytes))
            }
            emit(ModelDownloadEvent.Completed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(ModelDownloadEvent.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    override fun generateStreamingResponse(
        systemPrompt: String,
        conversationContext: String,
        userText: String,
    ): Flow<String> = flow {
        modelStore.resolveEngineModelPath().getOrThrow()
        val turnText = userText.trim().ifEmpty { "(El estudiante no dijo nada. Continúa la conversación.)" }
        val userContents = Contents.of(Content.Text(turnText))
        val conversation = createBoundedConversation(systemPrompt, conversationContext)

        try {
            var previousText = ""

            suspend fun emitDeltaUpTo(cleanText: String) {
                val cleanDelta = if (cleanText.startsWith(previousText)) {
                    cleanText.removePrefix(previousText)
                } else {
                    ""
                }

                if (cleanDelta.isNotEmpty()) {
                    emit(cleanDelta)
                }
            }

            conversation.sendMessageAsync(userContents).collect { message ->
                val text = stripLeadingAssistantLabel(message.textContent())

                val stopMatch = findStopMatch(text)

                if (stopMatch != null) {
                    Log.d(TAG, "Stream intercepted. Blocked generated marker: ${stopMatch.reason}")

                    val cleanText = text.substring(0, stopMatch.startIndex)
                    emitDeltaUpTo(cleanText)

                    runCatching { conversation.cancelProcess() }
                    throw StopGenerationException()
                }

                if (RepetitionDetector.isRepetitive(text)) {
                    Log.d(TAG, "Stream intercepted. Repetition loop detected in text: $text")

                    runCatching { conversation.cancelProcess() }
                    throw StopGenerationException()
                }

                val sentenceLimitEnd = findSentenceLimitEnd(text)
                if (sentenceLimitEnd != null) {
                    val cleanText = text.substring(0, sentenceLimitEnd + 1)
                    emitDeltaUpTo(cleanText)

                    Log.d(TAG, "Stream intercepted. Response sentence limit reached.")
                    runCatching { conversation.cancelProcess() }
                    throw StopGenerationException()
                }

                val delta = if (text.startsWith(previousText)) {
                    text.removePrefix(previousText)
                } else {
                    text
                }

                if (delta.isNotEmpty()) {
                    emit(delta)
                }
                previousText = text
            }
        } catch (e: StopGenerationException) {
            // Expected behavior. The model finished its turn and we successfully intercepted it.
            Log.d(TAG, "Generation finished cleanly via stop token.")
        } catch (e: CancellationException) {
            runCatching { conversation.cancelProcess() }
            throw e
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun resetConversation() {
        conversationMutex.withLock {
            runCatching { activeConversation?.close() }
            activeConversation = null
        }
    }

    private suspend fun createBoundedConversation(
        systemPrompt: String,
        conversationContext: String,
    ): Conversation = conversationMutex.withLock {
        runCatching { activeConversation?.close() }
        engineManager.createConversation(
            systemPrompt = systemPrompt,
            initialMessages = parseConversationHistory(conversationContext),
        ).also { conversation ->
            activeConversation = conversation
        }
    }

    private fun parseConversationHistory(conversationContext: String): List<Message> {
        if (conversationContext.isBlank()) return emptyList()

        val messages = conversationContext.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                when {
                    line.startsWith(LEARNER_PREFIX) ->
                        Message.user(line.removePrefix(LEARNER_PREFIX).trim())
                    line.startsWith(PARTNER_PREFIX) ->
                        line.removePrefix(PARTNER_PREFIX)
                            .trim()
                            .takeUnless(RepetitionDetector::isLowValueAcknowledgement)
                            ?.let(Message::model)
                    else -> null
                }
            }
            .toList()

        if (messages.isEmpty()) return emptyList()

        val withoutCurrentTurn = if (messages.last().role == Role.USER) {
            messages.dropLast(1)
        } else {
            messages
        }

        // Keep the rendered prompt comfortably inside Qwen2.5's mobile KV cache.
        return withoutCurrentTurn.takeLast(GemmaModelConfig.MAX_HISTORY_MESSAGES)
    }

    private fun Message.textContent(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    private fun stripLeadingAssistantLabel(text: String): String =
        LEADING_ASSISTANT_LABEL_PATTERN.replaceFirst(
            LEADING_QWEN_ASSISTANT_HEADER_PATTERN.replaceFirst(text, ""),
            "",
        )

    private fun findStopMatch(text: String): StopMatch? =
        STOP_PATTERNS
            .mapNotNull { pattern ->
                pattern.find(text)?.let { match ->
                    StopMatch(
                        startIndex = match.range.first,
                        reason = match.value,
                    )
                }
            }
            .minByOrNull { it.startIndex }

    private fun findSentenceLimitEnd(text: String): Int? {
        var sentenceCount = 0

        text.forEachIndexed { index, char ->
            if (char == '.' || char == '?' || char == '!') {
                sentenceCount += 1
                if (sentenceCount >= GemmaModelConfig.MAX_RESPONSE_SENTENCES) {
                    return index
                }
            }
        }

        return null
    }

    companion object {
        private const val TAG = "GemmaLlmRepository"
        private const val LEARNER_PREFIX = "Learner: "
        private const val PARTNER_PREFIX = "Partner: "

        private const val USER_LABELS =
            "Learner|User|Human|Student|Estudiante|Usuario|Alumno|Cliente"
        private const val ASSISTANT_LABELS =
            "Partner|Model|Assistant|Tutor|Profesor|Asistente|Modelo|" +
                "Camarero|Recepcionista|Entrevistador|Entrevistadora"

        private val LEADING_ASSISTANT_LABEL_PATTERN =
            Regex("^\\s*(?:$ASSISTANT_LABELS)\\s*:\\s*", RegexOption.IGNORE_CASE)

        private val LEADING_QWEN_ASSISTANT_HEADER_PATTERN =
            Regex(
                "^\\s*<\\|im_start\\|>\\s*(?:$ASSISTANT_LABELS)\\s*:?\\s*",
                RegexOption.IGNORE_CASE,
            )

        private val STOP_PATTERNS = listOf(
            Regex(
                "<(?:start|end)_of_turn>|<\\|im_(?:start|end)\\|>|<\\|endoftext\\|>",
                RegexOption.IGNORE_CASE,
            ),
            Regex("^\\s*(?:$USER_LABELS)\\s*:", RegexOption.IGNORE_CASE),
            Regex("\\n\\s*(?:$USER_LABELS|$ASSISTANT_LABELS)\\s*:", RegexOption.IGNORE_CASE),
        )
    }

    private data class StopMatch(
        val startIndex: Int,
        val reason: String,
    )

    /** Custom exception used to cleanly exit the coroutine collection without triggering an error state */
    private class StopGenerationException : CancellationException("Inference intercepted by stop token")
}