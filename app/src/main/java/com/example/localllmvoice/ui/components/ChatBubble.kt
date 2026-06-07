package com.example.localllmvoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localllmvoice.domain.model.ChatMessage

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    
    // Softer background containers with a gentle tint of the brand colors
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    }
    
    // Solid brand colors for typography provide high legibility with zero glare
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    // Subtle 1px borders aligned with the "Digital Traveler's Journal" tactile aesthetic
    val borderStroke = if (message.isUser) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
        Surface(
            color = containerColor,
            border = borderStroke,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        messages.forEach { message ->
            ChatBubble(message = message)
        }
    }
}
