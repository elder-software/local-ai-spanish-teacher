package com.eldersoftware.anytimespanish.ui.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldersoftware.anytimespanish.ui.components.AnimatedBrainIcon
import com.eldersoftware.anytimespanish.ui.theme.AnytimeSpanishTheme

@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedbackContent(uiState = uiState, onDone = onDone, modifier = modifier)
}

@Composable
private fun FeedbackContent(
    uiState: FeedbackUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            FeedbackTopRow(onClose = onDone)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (uiState) {
                    FeedbackUiState.Empty -> {
                        FeedbackHeader()
                        EmptyState()
                    }

                    is FeedbackUiState.Analyzing -> {
                        FeedbackHeader(topicTitle = uiState.topicTitle)
                        AnalyzingState(partialReport = uiState.partialReport)
                    }

                    is FeedbackUiState.Success -> {
                        FeedbackHeader(topicTitle = uiState.topicTitle)
                        FeedbackReportCard(
                            points = parseFeedbackPoints(uiState.report),
                            rawFallback = uiState.report,
                        )
                    }

                    is FeedbackUiState.Error -> {
                        FeedbackHeader()
                        ErrorCard(message = uiState.message)
                    }
                }
            }
            DoneButton(
                onClick = onDone,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun FeedbackTopRow(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cerrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackHeader(
    modifier: Modifier = Modifier,
    topicTitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Tu devolución",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!topicTitle.isNullOrBlank()) {
            Text(
                text = topicTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalyzingState(
    partialReport: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.secondary.copy(alpha = 0.14f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedBrainIcon(modifier = Modifier.size(36.dp))
            }
        }
        Text(
            text = "Analizando tu conversación…",
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )
        if (partialReport.isNotBlank()) {
            FeedbackReportCard(
                points = parseFeedbackPoints(partialReport),
                rawFallback = partialReport,
            )
        }
    }
}

@Composable
private fun FeedbackReportCard(
    points: List<String>,
    rawFallback: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (points.size > 1) {
                points.forEach { point ->
                    FeedbackPointRow(text = point)
                }
            } else {
                Text(
                    text = rawFallback,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FeedbackPointRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.tertiary,
            modifier = Modifier
                .padding(top = 10.dp)
                .size(8.dp),
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "No hay nada que analizar.",
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Nota",
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
private fun DoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(
            text = "Hecho",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun parseFeedbackPoints(report: String): List<String> =
    report.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.removePrefix("-").removePrefix("•").trim() }
        .filter { it.isNotEmpty() }

@Preview(showBackground = true)
@Composable
private fun FeedbackContentEmptyPreview() {
    AnytimeSpanishTheme {
        FeedbackContent(uiState = FeedbackUiState.Empty, onDone = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackContentAnalyzingPreview() {
    AnytimeSpanishTheme {
        FeedbackContent(
            uiState = FeedbackUiState.Analyzing(
                topicTitle = "En la cafetería",
                partialReport = "- Buen uso del pretérito imperfecto\n- Podrías practicar más conectores",
            ),
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackContentSuccessPreview() {
    AnytimeSpanishTheme {
        FeedbackContent(
            uiState = FeedbackUiState.Success(
                topicTitle = "En la cafetería",
                report = """
                    - Buen uso del pretérito imperfecto para describir el ambiente.
                    - Podrías practicar más conectores como "sin embargo".
                    - La pronunciación de la erre doble fue clara en la mayoría de frases.
                    - Intenta formular preguntas abiertas para alargar la conversación.
                """.trimIndent(),
            ),
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackContentErrorPreview() {
    AnytimeSpanishTheme {
        FeedbackContent(
            uiState = FeedbackUiState.Error(
                message = "No se pudo generar la devolución. Inténtalo de nuevo.",
            ),
            onDone = {},
        )
    }
}
