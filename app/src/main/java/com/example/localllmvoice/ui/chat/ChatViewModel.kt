package com.example.localllmvoice.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.audio.SpeechToTextEngine
import com.example.localllmvoice.data.audio.SttEvent
import com.example.localllmvoice.data.audio.TextToSpeechManager
import com.example.localllmvoice.data.repository.LlmRepository
import com.example.localllmvoice.domain.model.ChatMessage
import com.example.localllmvoice.domain.model.ConversationTopic
import com.example.localllmvoice.domain.model.FeedbackSession
import com.example.localllmvoice.domain.model.FeedbackSessionStore
import com.example.localllmvoice.domain.parser.GemmaStreamParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    fun toggleRecording() {
        val state = _uiState.value
        if (state !is ChatUiState.ActiveConversation || state.isGenerating) return

        if (state.isRecording) {
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
            interimTranscript = null,
        )
    }

    fun endConversation() {
        generationJob?.cancel()
        recognitionJob?.cancel()
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

    override fun onCleared() {
        generationJob?.cancel()
        recognitionJob?.cancel()
        textToSpeechManager.stop()
        if (!handingOffToFeedback) {
            runBlocking {
                llmRepository.resetConversation()
            }
        }
        super.onCleared()
    }

    private fun startListening(state: ChatUiState.ActiveConversation) {
        if (recognitionJob?.isActive == true) return
        if (!speechToTextManager.hasRecordPermission()) {
            _uiState.value = state.copy(errorMessage = "Microphone permission not granted")
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
        if (!current.isRecording) return
        _uiState.value = current.copy(interimTranscript = text)
    }

    private fun handleRecognitionFailure(message: String) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        _uiState.value = current.copy(
            isRecording = false,
            interimTranscript = null,
            errorMessage = message,
        )
    }

    private fun respondToTranscript(transcript: String) {
        val state = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val spokenText = transcript.trim()
        if (spokenText.isEmpty()) {
            _uiState.value = state.copy(
                isRecording = false,
                interimTranscript = null,
                errorMessage = "Didn't catch that — try again",
            )
            return
        }

        viewModelScope.launch {
            val normalizedSpokenText = runCatching {
                llmRepository.punctuateTranscript(spokenText)
            }.getOrDefault(spokenText).trim()

            val userMessage = ChatMessage(
                content = normalizedSpokenText,
                isUser = true,
            )
            val messagesWithUser = state.messages + userMessage
            _uiState.value = state.copy(
                messages = messagesWithUser,
                isRecording = false,
                isGenerating = true,
                interimTranscript = null,
            )

            val assistantMessageId = ChatMessage(isUser = false, content = "").id
            var assistantText = StringBuilder()
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
                                    messagesWithUser,
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
                    clearGenerating(messagesWithUser, assistantMessageId, assistantText.toString())
                }
            }
        }
    }

    private fun updateAssistantMessage(
        baseMessages: List<ChatMessage>,
        assistantId: String,
        content: String,
    ) {
        val current = _uiState.value as? ChatUiState.ActiveConversation ?: return
        val withoutPartial = current.messages.filterNot { !it.isUser && it.id == assistantId }
        val assistant = ChatMessage(id = assistantId, content = content, isUser = false)
        _uiState.value = current.copy(messages = withoutPartial + assistant)
    }

    private fun clearGenerating(
        baseMessages: List<ChatMessage>,
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
