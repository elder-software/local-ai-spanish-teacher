package com.eldersoftware.anytimespanish.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val translatedContent: String? = null,
    val isTranslating: Boolean = false,
)

data class ConversationTopic(
    val id: String,
    val title: String,
    val description: String,
    val openingMessage: String,
    val systemPrompt: String,
)

data class TopicCategory(
    val id: String,
    val title: String,
    val description: String,
    val isFree: Boolean = false,
    val topics: List<ConversationTopic>,
)
