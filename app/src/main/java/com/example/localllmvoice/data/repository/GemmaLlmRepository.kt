package com.example.localllmvoice.data.repository

import android.content.Context
import android.util.Log
import com.example.localllmvoice.data.gemma.DeviceCapabilityChecker
import com.example.localllmvoice.data.gemma.GemmaEngineManager
import com.example.localllmvoice.data.gemma.GemmaModelConfig
import com.example.localllmvoice.data.gemma.GemmaModelDownloader
import com.example.localllmvoice.data.gemma.GemmaModelStore
import com.example.localllmvoice.domain.model.FeedbackPrompt
import com.example.localllmvoice.domain.parser.RepetitionDetector
import com.example.localllmvoice.domain.parser.GemmaStreamParser
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.SamplerConfig
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

    fun checkDownloadedModelAvailability(): ModelAvailability {
        modelStore.ensureModelDirectory()
        val capability = capabilityChecker.evaluate()

        if (!capability.canRunConfiguredModel) {
            return ModelAvailability(
                status = GemmaModelStatus.INSUFFICIENT_DEVICE,
                message = "Device below recommended specs for ${GemmaModelConfig.MODEL_LABEL}",
                deviceCapability = capability,
            )
        }

        if (!modelStore.isModelReady()) {
            return ModelAvailability(
                status = GemmaModelStatus.DOWNLOAD_REQUIRED,
                message = "Download ${GemmaModelConfig.MODEL_LABEL} (~${GemmaModelConfig.ESTIMATED_SIZE_MB} MB) to run offline",
                deviceCapability = capability,
                totalBytes = GemmaModelConfig.ESTIMATED_SIZE_BYTES,
            )
        }

        return ModelAvailability(
            status = GemmaModelStatus.READY,
            message = "${GemmaModelConfig.MODEL_LABEL} downloaded",
            deviceCapability = capability,
        )
    }

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

    override fun analyzeConversation(transcript: String): Flow<String> = flow {
        conversationMutex.withLock {
            modelStore.resolveEngineModelPath().getOrThrow()
            val userContents = Contents.of(Content.Text(transcript))
            val analysisSampler = SamplerConfig(
                topK = GemmaModelConfig.ANALYSIS_SAMPLER_TOP_K,
                topP = GemmaModelConfig.ANALYSIS_SAMPLER_TOP_P,
                temperature = GemmaModelConfig.ANALYSIS_SAMPLER_TEMPERATURE,
            )
            val conversation = replaceActiveConversation(
                systemPrompt = FeedbackPrompt.buildAnalysisSystemPrompt(),
                initialMessages = emptyList(),
                samplerConfig = analysisSampler,
            )

            try {
                var previousText = ""
                val parser = GemmaStreamParser()

                conversation.sendMessageAsync(userContents).collect { message ->
                    val text = stripLeadingAssistantLabel(message.textContent())
                    val delta = if (text.startsWith(previousText)) {
                        text.removePrefix(previousText)
                    } else {
                        text
                    }
                    previousText = text
                    val visibleText = StringBuilder()
                    parser.processToken(delta) { visible ->
                        if (visible.isNotEmpty()) {
                            visibleText.append(visible)
                        }
                    }
                    if (visibleText.isNotEmpty()) {
                        emit(visibleText.toString())
                    }
                }
            } catch (e: CancellationException) {
                runCatching { conversation.cancelProcess() }
                throw e
            }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun generateNextReplySuggestion(
        topicTitle: String,
        conversationContext: String,
        avoidSuggestions: List<String>,
    ): String = conversationMutex.withLock {
        val modelPath = modelStore.resolveEngineModelPath().getOrThrow()
        engineManager.initialize(modelPath)
        val suggestionSampler = SamplerConfig(
            topK = GemmaModelConfig.SUGGESTION_SAMPLER_TOP_K,
            topP = GemmaModelConfig.SUGGESTION_SAMPLER_TOP_P,
            temperature = GemmaModelConfig.SUGGESTION_SAMPLER_TEMPERATURE,
        )
        val conversation = replaceActiveConversation(
            systemPrompt = NEXT_REPLY_SUGGESTION_SYSTEM_PROMPT,
            initialMessages = emptyList(),
            samplerConfig = suggestionSampler,
        )

        val userPayload = buildString {
            appendLine("Tema de práctica: ${topicTitle.trim()}")
            appendLine()
            appendLine(
                "En la transcripción, \"Partner\" es el personaje simulado y \"Learner\" es el estudiante humano " +
                    "que interpreta a la otra persona de la situación.",
            )
            appendLine()
            appendLine("Transcripción hasta ahora:")
            append(conversationContext.trim().ifEmpty { "(La conversación acaba de empezar.)" })
            if (avoidSuggestions.isNotEmpty()) {
                appendLine()
                appendLine("Sugerencias ya mostradas (propón algo diferente):")
                avoidSuggestions.forEach { suggestion ->
                    appendLine("- ${suggestion.trim()}")
                }
            }
        }

        try {
            val parser = GemmaStreamParser()
            var previousText = ""
            val suggestion = StringBuilder()

            conversation.sendMessageAsync(Contents.of(Content.Text(userPayload))).collect { message ->
                val text = stripLeadingAssistantLabel(message.textContent())
                val delta = if (text.startsWith(previousText)) {
                    text.removePrefix(previousText)
                } else {
                    text
                }
                previousText = text
                parser.processToken(delta) { visible ->
                    if (visible.isNotEmpty()) {
                        suggestion.append(visible)
                    }
                }
            }

            sanitizeSuggestion(suggestion.toString())
        } finally {
            closeActiveConversation()
        }
    }

    override suspend fun punctuateTranscript(transcript: String): String {
        val rawTranscript = transcript.trim()
        if (rawTranscript.isEmpty()) return rawTranscript

        return conversationMutex.withLock {
            val modelPath = modelStore.resolveEngineModelPath().getOrThrow()
            engineManager.initialize(modelPath)
            val punctuateSampler = SamplerConfig(
                topK = 1,
                topP = 0.1,
                temperature = 0.0,
            )
            val conversation = replaceActiveConversation(
                systemPrompt = TRANSCRIPT_PUNCTUATION_SYSTEM_PROMPT,
                samplerConfig = punctuateSampler,
            )

            try {
                val parser = GemmaStreamParser()
                var previousText = ""
                val punctuated = StringBuilder()

                conversation.sendMessageAsync(Contents.of(Content.Text(rawTranscript))).collect { message ->
                    val text = stripLeadingAssistantLabel(message.textContent())
                    val delta = if (text.startsWith(previousText)) {
                        text.removePrefix(previousText)
                    } else {
                        text
                    }
                    previousText = text
                    parser.processToken(delta) { visible ->
                        if (visible.isNotEmpty()) {
                            punctuated.append(visible)
                        }
                    }
                }

                punctuated.toString().trim().ifEmpty { rawTranscript }
            } finally {
                closeActiveConversation()
            }
        }
    }

    override suspend fun translateText(text: String, targetLanguage: String): String {
        val rawText = text.trim()
        if (rawText.isEmpty()) return rawText

        return conversationMutex.withLock {
            val modelPath = modelStore.resolveEngineModelPath().getOrThrow()
            engineManager.initialize(modelPath)
            val translateSampler = SamplerConfig(
                topK = 1,
                topP = 0.1,
                temperature = 0.0,
            )
            val systemPrompt = """
                Eres un traductor profesional y preciso. Tu única tarea es traducir el texto proporcionado al idioma: $targetLanguage.
                
                REGLAS ESTRICTAS:
                1. Traduce el texto de entrada de manera exacta y natural al idioma especificado.
                2. Devuelve ÚNICAMENTE la traducción. No agregues explicaciones, notas, comentarios, comillas adicionales ni introducciones.
                3. Conserva el tono y el significado del texto original.
            """.trimIndent()
            
            val conversation = replaceActiveConversation(
                systemPrompt = systemPrompt,
                samplerConfig = translateSampler,
            )

            try {
                val parser = GemmaStreamParser()
                var previousText = ""
                val translated = StringBuilder()

                conversation.sendMessageAsync(Contents.of(Content.Text(rawText))).collect { message ->
                    val text = stripLeadingAssistantLabel(message.textContent())
                    val delta = if (text.startsWith(previousText)) {
                        text.removePrefix(previousText)
                    } else {
                        text
                    }
                    previousText = text
                    parser.processToken(delta) { visible ->
                        if (visible.isNotEmpty()) {
                            translated.append(visible)
                        }
                    }
                }

                translated.toString().trim().ifEmpty { rawText }
            } finally {
                closeActiveConversation()
            }
        }
    }

    override suspend fun resetConversation() {
        conversationMutex.withLock {
            closeActiveConversation()
        }
    }

    private suspend fun createBoundedConversation(
        systemPrompt: String,
        conversationContext: String,
    ): Conversation = conversationMutex.withLock {
        replaceActiveConversation(
            systemPrompt = systemPrompt,
            initialMessages = parseConversationHistory(conversationContext),
        )
    }

    private suspend fun replaceActiveConversation(
        systemPrompt: String,
        initialMessages: List<Message> = emptyList(),
        samplerConfig: SamplerConfig = SamplerConfig(
            topK = GemmaModelConfig.SAMPLER_TOP_K,
            topP = GemmaModelConfig.SAMPLER_TOP_P,
            temperature = GemmaModelConfig.SAMPLER_TEMPERATURE,
        ),
    ): Conversation {
        closeActiveConversation()
        return engineManager.createConversation(
            systemPrompt = systemPrompt,
            initialMessages = initialMessages,
            samplerConfig = samplerConfig,
        ).also { conversation ->
            activeConversation = conversation
        }
    }

    private fun closeActiveConversation() {
        runCatching { activeConversation?.cancelProcess() }
        runCatching { activeConversation?.close() }
        activeConversation = null
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

        // Keep the rendered prompt comfortably inside the configured model's mobile KV cache.
        return withoutCurrentTurn.takeLast(GemmaModelConfig.MAX_HISTORY_MESSAGES)
    }

    private fun Message.textContent(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    private fun stripLeadingAssistantLabel(text: String): String =
        LEADING_ASSISTANT_LABEL_PATTERN.replaceFirst(
            LEADING_ASSISTANT_HEADER_PATTERN.replaceFirst(text, ""),
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

    private fun sanitizeSuggestion(raw: String): String {
        val firstLine = raw
            .lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("• ")
                    .removePrefix("* ")
                    .trim('"', '\'', '«', '»')
                    .replace(LEADING_ASSISTANT_LABEL_PATTERN, "")
                    .trim()
            }
            .firstOrNull { it.isNotBlank() }
            ?: return ""

        return firstLine
            .replace(Regex("^\\s*(?:Sugerencia|Respuesta|Try saying|Prueba diciendo)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('"', '\'', '«', '»')
    }

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
        private val NEXT_REPLY_SUGGESTION_SYSTEM_PROMPT = """
            Eres un coach de español independiente. Tu única tarea es sugerir qué podría decir el estudiante humano (Learner) a continuación en una conversación de práctica por roles.

            CONTEXTO DE ROLES:
            - "Partner" en la transcripción es el personaje simulado (camarero, entrevistador, recepcionista, etc.).
            - "Learner" es el estudiante humano que interpreta a la otra persona de la situación (cliente, candidato, huésped, turista, etc.).
            - Tu sugerencia debe ser lo que Learner diría en primera persona, NO lo que Partner diría.

            REGLAS ESTRICTAS:
            1. Devuelve UNA sola frase corta en español natural (es-ES) que Learner podría decir en voz alta como su siguiente turno.
            2. Responde a la última frase de Partner con un pedido, una respuesta, una confirmación o información concreta del rol de Learner.
            3. Prefiere afirmaciones, pedidos o respuestas directas. NO termines con una pregunta salvo que el rol de Learner necesite preguntar algo concreto (precio, disponibilidad, dirección).
            4. NUNCA sugieras una frase que Partner diría al Learner. Si Partner pregunta qué quiere beber, Learner pide una bebida; Learner NO vuelve a preguntar qué comida quiere el cliente.
            5. NO copies reglas del escenario sobre cómo debe hablar Partner. Ignora cualquier instrucción de terminar con preguntas.
            6. NO traduzcas al inglés. NO expliques gramática. NO añadas comentarios, etiquetas ni formato de diálogo.
            7. Si recibes sugerencias previas, propón una alternativa distinta en tono o contenido.
            8. Usa como máximo dos frases cortas.

            EJEMPLO:
            Partner: ¿Qué te gustaría beber?
            Sugerencia correcta: Quiero una cerveza, por favor.
            Sugerencia incorrecta: ¿Qué tipo de comida te apetece probar?
        """.trimIndent()
        private val TRANSCRIPT_PUNCTUATION_SYSTEM_PROMPT = """
            Eres un corrector de puntuación para transcripciones de voz en español. Tu única tarea es añadir mayúsculas y signos de puntuación (¿, ?, ¡, !, ,, .).

            REGLAS ESTRICTAS:
            1. No cambies ninguna palabra: NUNCA modifiques, corrijas, agregues ni elimines palabras. Conserva exactamente las mismas palabras que recibes, en el mismo orden. Por ejemplo, "me gusta" se queda como "me gusta", y NUNCA lo cambies por "mi costa".
            2. Nada de ortografía: No corrijas errores ortográficos. Aunque una palabra parezca mal escrita, déjala tal cual. Tu trabajo es exclusivamente la puntuación y el uso de mayúsculas.
            3. Prohibido reescribir: No mejores el estilo ni reescribas frases. Las palabras de salida deben ser idénticas a las de entrada; lo único que puede cambiar son las mayúsculas y los signos de puntuación.
            4. Uso de preguntas (¿?): Sé muy conservador con los signos de interrogación. Solo utilízalos si la frase contiene palabras interrogativas explícitas (como qué, cómo, cuándo, dónde, quién, por qué, cuál) o si la estructura es indudablemente una pregunta. En caso de duda, usa un punto final (.) en lugar de signos de interrogación.
            5. Formato de salida: Devuelve exclusivamente el texto con puntuación, sin explicaciones ni comentarios.
        """.trimIndent()

        private const val USER_LABELS =
            "Learner|User|Human|Student|Estudiante|Usuario|Alumno|Cliente"
        private const val ASSISTANT_LABELS =
            "Partner|Model|Assistant|Tutor|Profesor|Asistente|Modelo|" +
                "Camarero|Recepcionista|Entrevistador|Entrevistadora"

        private val LEADING_ASSISTANT_LABEL_PATTERN =
            Regex("^\\s*(?:$ASSISTANT_LABELS)\\s*:\\s*", RegexOption.IGNORE_CASE)

        // Strips a leaked assistant turn header, e.g. Gemma 4 "<|turn>model" or Qwen "<|im_start|>assistant".
        private val LEADING_ASSISTANT_HEADER_PATTERN =
            Regex(
                "^\\s*(?:<\\|turn>|<\\|im_start\\|>)\\s*(?:$ASSISTANT_LABELS)?\\s*:?\\s*",
                RegexOption.IGNORE_CASE,
            )

        private val STOP_PATTERNS = listOf(
            Regex(
                // Gemma 4 turn/thinking markers, plus legacy Gemma/Qwen markers as fallbacks.
                "<\\|turn>|<turn\\|>|<\\|think\\|>|<\\|channel>|<channel\\|>|" +
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