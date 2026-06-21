package com.eldersoftware.anytimespanish.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldersoftware.anytimespanish.data.audio.SpeechToTextEngine
import com.eldersoftware.anytimespanish.data.audio.SttEvent
import com.eldersoftware.anytimespanish.data.audio.TextToSpeechManager
import com.eldersoftware.anytimespanish.data.repository.LlmRepository
import com.eldersoftware.anytimespanish.domain.model.ChatMessage
import com.eldersoftware.anytimespanish.domain.model.ConversationTopic
import com.eldersoftware.anytimespanish.domain.model.FeedbackSession
import com.eldersoftware.anytimespanish.domain.model.FeedbackSessionStore
import com.eldersoftware.anytimespanish.domain.parser.GemmaStreamParser
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val topic: ConversationTopic,
    private val llmRepository: LlmRepository,
    private val speechToTextManager: SpeechToTextEngine,
    private val textToSpeechManager: TextToSpeechManager,
    private val feedbackSessionStore: FeedbackSessionStore,
) : ViewModel() {
    private val streamParser = GemmaStreamParser()
    private var generationJob: Job? = null
    private var recognitionJob: Job? = null
    private var suggestionJob: Job? = null
    private var suggestionsThisTurn: List<String> = emptyList()
    private var discardPendingTranscript = false
    private var systemPrompt: String = topic.systemPrompt
    private var handingOffToFeedback = false

    private val _uiState = MutableStateFlow<ChatUiState>(
        ChatUiState.Initializing,
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        selectTopic(topic)
    }

    fun selectTopic(selected: ConversationTopic) {
        viewModelScope.launch {
            llmRepository.resetConversation()
        }
        systemPrompt = selected.systemPrompt
        streamParser.reset()
        textToSpeechManager.stop()
        suggestionsThisTurn = emptyList()
        _uiState.value = ChatUiState.ActiveConversation(
            messages = listOf(
                ChatMessage(
                    content = selected.openingMessage,
                    isUser = false,
                ),
            ),
            currentTopic = selected.title,
        )
        textToSpeechManager.enqueue(selected.openingMessage)
    }

    fun toggleSuggestion() {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        if (state.isRecording || state.isTranscribing || state.isGenerating) return

        if (state.isGeneratingSuggestion) {
            suggestionJob?.cancel()
            suggestionJob = null
            _uiState.value = clearSuggestion(state)
            return
        }

        if (state.isSuggestionVisible) {
            requestSuggestion(state, refresh = true)
            return
        }

        requestSuggestion(state, refresh = false)
    }

    private fun requestSuggestion(
        state: ChatUiState.ActiveConversation,
        refresh: Boolean,
    ) {
        _uiState.value = state.copy(
            isSuggestionVisible = true,
            isGeneratingSuggestion = true,
            suggestedReply = if (refresh) state.suggestedReply else null,
            errorMessage = null,
        )

        suggestionJob = viewModelScope.launch {
            try {
                val currentState = _uiState.value as? ChatUiState.ActiveConversation ?: return@launch
                val suggestion = llmRepository.generateNextReplySuggestion(
                    topicTitle = currentState.currentTopic,
                    conversationContext = formatConversationContext(currentState.messages),
                    avoidSuggestions = suggestionsThisTurn,
                )
                val trimmed = suggestion.trim()
                val latest = _uiState.value as? ChatUiState.ActiveConversation ?: return@launch
                if (!latest.isSuggestionVisible && !latest.isGeneratingSuggestion) return@launch
                suggestionsThisTurn = suggestionsThisTurn + trimmed
                _uiState.value = latest.copy(
                    suggestedReply = trimmed,
                    isGeneratingSuggestion = false,
                    isSuggestionVisible = true,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val current = _uiState.value as? ChatUiState.ActiveConversation ?: return@launch
                _uiState.value = current.copy(
                    isGeneratingSuggestion = false,
                    isSuggestionVisible = false,
                    suggestedReply = null,
                    errorMessage = "Could not generate a suggestion",
                )
            } finally {
                suggestionJob = null
            }
        }
    }

    fun toggleRecording() {
        val state = _uiState.value
        if (state !is ChatUiState.ActiveConversation || state.isGenerating || state.isTranscribing) return

        if (state.isRecording) {
            // Reflect the finalizing phase immediately so the UI doesn't appear frozen
            // while the recogniser flushes and settles the final transcript.
            _uiState.value = clearSuggestion(
                state.copy(isRecording = false, isTranscribing = true),
            )
            suggestionsThisTurn = emptyList()
            speechToTextManager.stopListening()
        } else {
            startListening(state)
        }
    }

    fun cancelVoiceInput() {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        if (!state.isRecording) return

        discardPendingTranscript = true
        speechToTextManager.stopListening()
        _uiState.value = state.copy(
            isRecording = false,
            isTranscribing = false,
            interimTranscript = null,
            inputLevel = 0f,
        )
    }

    fun endConversation() {
        generationJob?.cancel()
        recognitionJob?.cancel()
        suggestionJob?.cancel()
        textToSpeechManager.stop()
        if (!handingOffToFeedback) {
            viewModelScope.launch {
                llmRepository.resetConversation()
            }
        }
    }

    fun prepareFeedback(): Boolean {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return false
        if (state.messages.none { it.isUser }) {
            return false
        }

        feedbackSessionStore.set(
            FeedbackSession(
                topicTitle = state.currentTopic,
                transcript = formatConversationContext(state.messages),
            ),
        )
        handingOffToFeedback = true
        return true
    }

    fun dismissError() {
        val state = _uiState.value
        if (state is ChatUiState.ActiveConversation) {
            _uiState.value = state.copy(errorMessage = null)
        }
    }

    fun dismissMicrophonePermissionDialog() {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = state.copy(
            showMicrophonePermissionDialog = false,
            microphonePermissionNeedsSettings = false,
        )
    }

    fun onMicrophonePermissionGranted() {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = state.copy(
            showMicrophonePermissionDialog = false,
            microphonePermissionNeedsSettings = false,
        )
        startListening(_uiState.value as ChatUiState.ActiveConversation)
    }

    fun onMicrophonePermissionDenied(needsSettings: Boolean) {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = state.copy(
            showMicrophonePermissionDialog = true,
            microphonePermissionNeedsSettings = needsSettings,
        )
    }

    fun translateMessage(messageId: String) {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val message = state.messages.find { it.id == messageId } ?: return
        if (message.translatedContent != null || message.isTranslating) return

        val updatedMessages = state.messages.map { msg ->
            if (msg.id == messageId) msg.copy(isTranslating = true) else msg
        }
        _uiState.value = state.copy(messages = updatedMessages)

        viewModelScope.launch {
            try {
                val targetLanguage = java.util.Locale.getDefault().displayLanguage.ifBlank { "English" }
                val translation = llmRepository.translateText(message.content, targetLanguage)
                
                val current = _uiState.value as? ChatUiState.ActiveConversation ?: return@launch
                val finalMessages = current.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(isTranslating = false, translatedContent = translation)
                    } else {
                        msg
                    }
                }
                _uiState.value = current.copy(messages = finalMessages)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val current = _uiState.value as? ChatUiState.ActiveConversation ?: return@launch
                val finalMessages = current.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(isTranslating = false) else msg
                }
                _uiState.value = current.copy(
                    messages = finalMessages,
                    errorMessage = "Translation failed: ${e.message}"
                )
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        generationJob?.cancel()
        recognitionJob?.cancel()
        suggestionJob?.cancel()
        textToSpeechManager.stop()
        if (!handingOffToFeedback) {
            // viewModelScope is already cancelled here, and blocking with runBlocking froze
            // navigation while the LLM session cancelled and closed. The reset must outlive
            // this ViewModel, so fire-and-forget on GlobalScope; resetConversation is
            // idempotent, so racing an earlier reset from endConversation() is harmless.
            GlobalScope.launch(Dispatchers.Default) {
                llmRepository.resetConversation()
            }
        }
        super.onCleared()
    }

    private fun startListening(state: ChatUiState.ActiveConversation) {
        if (recognitionJob?.isActive == true) return
        if (!speechToTextManager.hasRecordPermission()) {
            _uiState.value = state.copy(
                showMicrophonePermissionDialog = true,
                microphonePermissionNeedsSettings = false,
                errorMessage = null,
            )
            return
        }
        if (!speechToTextManager.isAvailable()) {
            _uiState.value = state.copy(
                errorMessage = "Speech recognition is unavailable on this device",
            )
            return
        }

        textToSpeechManager.stop()
        discardPendingTranscript = false
        _uiState.value = state.copy(
            isRecording = true,
            interimTranscript = "",
            inputLevel = 0f,
            errorMessage = null,
        )

        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            try {
                speechToTextManager.transcribe(RECOGNITION_LANGUAGE).collect { event ->
                    when (event) {
                        is SttEvent.Partial -> {
                            if (!discardPendingTranscript) {
                                updateInterim(event.text)
                            }
                        }
                        is SttEvent.AudioLevel -> {
                            if (!discardPendingTranscript) {
                                updateInputLevel(event.rms)
                            }
                        }
                        is SttEvent.Final -> {
                            if (discardPendingTranscript) {
                                discardPendingTranscript = false
                            } else {
                                respondToTranscript(event.text)
                            }
                        }
                        is SttEvent.Failure -> {
                            if (discardPendingTranscript) {
                                discardPendingTranscript = false
                            } else {
                                handleRecognitionFailure(event.message)
                            }
                        }
                    }
                }
            } finally {
                recognitionJob = null
                discardPendingTranscript = false
            }
        }
    }

    private fun updateInterim(text: String) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        // Accepted during the transcribing phase too, so any late partials emitted while the
        // recogniser finalizes still refresh the on-screen text.
        if (!current.isRecording && !current.isTranscribing) return
        _uiState.value = current.copy(interimTranscript = text)
    }

    private fun updateInputLevel(rms: Float) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        if (!current.isRecording) return
        _uiState.value = current.copy(inputLevel = rms)
    }

    private fun handleRecognitionFailure(message: String) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = current.copy(
            isRecording = false,
            isTranscribing = false,
            interimTranscript = null,
            errorMessage = message,
        )
        // A Failure is terminal for this recognition session, but the manager leaves the flow
        // open. Cancel the collector so the flow's awaitClose tears down the recorder and feed
        // thread; otherwise recognitionJob stays active and blocks the next recording attempt.
        recognitionJob?.cancel()
    }

    private fun respondToTranscript(transcript: String) {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val spokenText = transcript.trim()
        if (spokenText.isEmpty()) {
            _uiState.value = state.copy(
                isRecording = false,
                isTranscribing = false,
                interimTranscript = null,
                errorMessage = "Didn't catch that — try again",
            )
            return
        }

        viewModelScope.launch {
            val normalizedSpokenText = runCatching {
                llmRepository.correctTranscript(
                    transcript = spokenText,
                    topicTitle = state.currentTopic,
                    conversationContext = formatConversationContext(state.messages),
                )
            }.getOrDefault(spokenText).trim()

            val userMessage = ChatMessage(
                content = normalizedSpokenText,
                isUser = true,
            )
            val messagesWithUser = state.messages + userMessage
            suggestionJob?.cancel()
            suggestionJob = null
            suggestionsThisTurn = emptyList()
            _uiState.value = clearSuggestion(
                state.copy(
                    messages = messagesWithUser,
                    isRecording = false,
                    isTranscribing = false,
                    isGenerating = true,
                    interimTranscript = null,
                ),
            )

            val assistantMessageId = ChatMessage(isUser = false, content = "").id
            val assistantText = StringBuilder()
            val speechBuffer = StringBuilder()

            streamParser.reset()
            textToSpeechManager.stop()
            generationJob = launch {
                try {
                    llmRepository.generateStreamingResponse(
                        systemPrompt = systemPrompt,
                        conversationContext = formatConversationContext(messagesWithUser),
                        userText = normalizedSpokenText,
                    ).collect { token ->
                        streamParser.processToken(token) { visible ->
                            if (visible.isNotEmpty()) {
                                assistantText.append(visible)
                                speechBuffer.append(visible)
                                updateAssistantMessage(
                                    assistantMessageId,
                                    assistantText.toString(),
                                )
                                drainSpeakableSentences(speechBuffer)
                                    .forEach(textToSpeechManager::enqueue)
                            }
                        }
                    }

                    val finalText = assistantText.toString().trim()
                    if (finalText.isNotEmpty()) {
                        val remainingSpeech = speechBuffer.toString().trim()
                        if (remainingSpeech.isNotEmpty()) {
                            textToSpeechManager.enqueue(remainingSpeech)
                            speechBuffer.setLength(0)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    updateActiveError(messagesWithUser, e.message ?: "Could not generate a response")
                } finally {
                    clearGenerating(assistantMessageId, assistantText.toString())
                }
            }
        }
    }

    private fun updateAssistantMessage(
        assistantId: String,
        content: String,
    ) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val withoutPartial = current.messages.filterNot { !it.isUser && it.id == assistantId }
        val assistant = ChatMessage(id = assistantId, content = content, isUser = false)
        _uiState.value = current.copy(messages = withoutPartial + assistant)
    }

    private fun clearGenerating(
        assistantId: String,
        finalContent: String,
    ) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val trimmed = finalContent.trim()
        val messages = if (trimmed.isEmpty()) {
            current.messages.filterNot { !it.isUser && it.id == assistantId }
        } else {
            current.messages.filterNot { !it.isUser && it.id == assistantId } +
                ChatMessage(id = assistantId, content = trimmed, isUser = false)
        }
        _uiState.value = current.copy(messages = messages, isGenerating = false)
    }

    private fun updateActiveError(baseMessages: List<ChatMessage>, message: String) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = current.copy(
            messages = baseMessages,
            isGenerating = false,
            isRecording = false,
            errorMessage = message,
        )
    }

    private fun clearSuggestion(state: ChatUiState.ActiveConversation): ChatUiState.ActiveConversation =
        state.copy(
            suggestedReply = null,
            isSuggestionVisible = false,
            isGeneratingSuggestion = false,
        )

    private fun formatConversationContext(messages: List<ChatMessage>): String =
        messages.joinToString(separator = "\n") { message ->
            val role = if (message.isUser) "Learner" else "Partner"
            "$role: ${message.content}"
        }

    private fun drainSpeakableSentences(buffer: StringBuilder): List<String> {
        val sentences = mutableListOf<String>()
        while (true) {
            val endIndex = buffer.indexOfFirstSentenceEnd()
            if (endIndex < 0) break

            val sentence = buffer.substring(0, endIndex + 1).trim()
            buffer.delete(0, endIndex + 1)
            if (sentence.isNotEmpty()) {
                sentences += sentence
            }
        }
        return sentences
    }

    private fun StringBuilder.indexOfFirstSentenceEnd(): Int {
        for (index in indices) {
            if (this[index] == '.' || this[index] == '?' || this[index] == '!' || this[index] == '\n') {
                return index
            }
        }
        return -1
    }

    private companion object {
        private const val RECOGNITION_LANGUAGE = "es-ES"
    }
}
