package com.eldersoftware.anytimespanish.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eldersoftware.anytimespanish.domain.model.ChatMessage

@Composable
fun ChatBubble(
    message: ChatMessage,
    onTranslateClick: () -> Unit,
    isTranslateEnabled: Boolean,
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
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
                
                if (message.translatedContent != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = textColor.copy(alpha = 0.15f)
                    )
                    Text(
                        text = message.translatedContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.8f),
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (message.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = textColor
                        )
                    } else {
                        TextButton(
                            onClick = onTranslateClick,
                            enabled = isTranslateEnabled,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "Translate",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isTranslateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    onTranslateClick: (String) -> Unit,
    isTranslateEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        messages.forEach { message ->
            ChatBubble(
                message = message,
                onTranslateClick = { onTranslateClick(message.id) },
                isTranslateEnabled = isTranslateEnabled,
            )
        }
    }
}
