package com.example.localllmvoice.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ConversationTopic(
    val id: String,
    val title: String,
    val description: String,
    val openingMessage: String,
    val systemPrompt: String,
)
