package com.eldersoftware.anytimespanish.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldersoftware.anytimespanish.domain.model.ConversationTopic
import com.eldersoftware.anytimespanish.domain.model.ConversationTopics
import com.eldersoftware.anytimespanish.domain.model.TopicCategory
import com.eldersoftware.anytimespanish.ui.components.AnimatedBrainIcon
import com.eldersoftware.anytimespanish.ui.theme.AnytimeSpanishTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTopicSelected: (ConversationTopic) -> Unit,
    onUnlockRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onTopicSelected = onTopicSelected,
        onUnlockRequested = onUnlockRequested,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onTopicSelected: (ConversationTopic) -> Unit,
    onUnlockRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep a stable non-null status while the card is exiting so the height animation
    // has real content to shrink. The VM often nulls modelStatus at the same time it
    // sets isCardVisible=false (e.g. the 3s auto-dismiss of the Ready banner).
    var displayedModelStatus by remember { mutableStateOf(uiState.modelStatus) }
    LaunchedEffect(uiState.modelStatus, uiState.isCardVisible) {
        if (uiState.modelStatus != null && uiState.isCardVisible) {
            displayedModelStatus = uiState.modelStatus
        }
        // When hiding we intentionally leave the last value so AnimatedVisibility's
        // exit transition can still render a full card while the Box animates height.
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                DashboardHeader()
            }

            item(key = "status") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    AnimatedVisibility(
                        visible = uiState.modelStatus != null && uiState.isCardVisible,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        displayedModelStatus?.let { status ->
                            ModelStatusCard(modelStatus = status)
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                item(key = "error") {
                    DashboardMessageCard(message = error)
                }
            }

            item(key = "topics_header") {
                TopicSectionHeader(canStartConversation = uiState.canStartConversation)
            }

            uiState.categories.forEach { category ->
                val unlocked = uiState.isCategoryUnlocked(category)
                item(key = "cat_${category.id}") {
                    CategorySectionHeader(category)
                }
                items(category.topics, key = { "${category.id}_${it.id}" }) { topic ->
                    TopicCard(
                        topic = topic,
                        enabled = uiState.canStartConversation,
                        locked = !unlocked,
                        onClick = if (unlocked) {
                            { onTopicSelected(topic) }
                        } else {
                            onUnlockRequested
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Anytime Spanish",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Your pocket Spanish conversation partner.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelStatusCard(
    modelStatus: DashboardUiState.UiModelState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor: androidx.compose.ui.graphics.Color
    val iconContainerColor: androidx.compose.ui.graphics.Color
    val title: String
    val body: String

    when (modelStatus) {
        DashboardUiState.UiModelState.Ready -> {
            containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            iconContainerColor = colorScheme.tertiary
            title = "Everything is packed and ready."
            body = "Your tutor is available now, so you can jump into any scene below and start speaking."
        }

        DashboardUiState.UiModelState.Loading -> {
            containerColor = colorScheme.secondaryContainer.copy(alpha = 0.45f)
            iconContainerColor = colorScheme.secondary
            title = "Warming up your private tutor."
            body = "The on-device models are still getting ready. Topics will open as soon as setup finishes."
        }

        DashboardUiState.UiModelState.Error -> {
            containerColor = colorScheme.errorContainer
            iconContainerColor = colorScheme.error
            title = "Something interrupted setup."
            body = "Check the note below for details, then try again once the model issue is resolved."
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor.copy(alpha = 0.14f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (modelStatus == DashboardUiState.UiModelState.Loading) {
                        AnimatedBrainIcon(modifier = Modifier.size(36.dp))
                    } else {
                        Icon(
                            imageVector = if (modelStatus == DashboardUiState.UiModelState.Ready) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = iconContainerColor,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DashboardMessageCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface,
        ),
        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Setup note",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategorySectionHeader(
    category: TopicCategory,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth(0.2f)
                .align(Alignment.CenterHorizontally),
            color = colorScheme.outlineVariant.copy(alpha = 0.72f),
            thickness = 4.dp,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onBackground,
                )
                if (category.isFree) {
                    FreeBadge()
                }
            }
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FreeBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = "Free",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TopicSectionHeader(
    canStartConversation: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Choose today's conversation",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (canStartConversation) {
                "Pick a scene that sounds interesting and begin when you are ready."
            } else {
                "Your tutor is still warming up. You can browse the scenes now, and they will unlock as soon as setup completes."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicCard(
    topic: ConversationTopic,
    enabled: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isInteractive = locked || enabled

    Card(
        onClick = onClick,
        enabled = isInteractive,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface,
            disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isInteractive) {
                colorScheme.primary.copy(alpha = 0.14f)
            } else {
                colorScheme.outlineVariant.copy(alpha = 0.65f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = if (isInteractive) {
                        colorScheme.secondaryContainer
                    } else {
                        colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = if (locked) {
                                Icons.Filled.Lock
                            } else {
                                Icons.AutoMirrored.Filled.ArrowForward
                            },
                            contentDescription = null,
                            tint = if (isInteractive) {
                                colorScheme.onSecondaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.padding(end = 60.dp),
                text = topic.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentReadyPreview() {
    AnytimeSpanishTheme {
        DashboardContent(
            uiState = DashboardUiState(
                categories = ConversationTopics.categories.take(2),
                modelStatus = null,
                errorMessage = null,
                isCardVisible = false,
            ),
            onTopicSelected = {},
            onUnlockRequested = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentLoadingPreview() {
    AnytimeSpanishTheme {
        DashboardContent(
            uiState = DashboardUiState(
                categories = ConversationTopics.categories.take(2),
                modelStatus = DashboardUiState.UiModelState.Loading,
                errorMessage = null,
                isCardVisible = true,
            ),
            onTopicSelected = {},
            onUnlockRequested = {},
        )
    }
}
