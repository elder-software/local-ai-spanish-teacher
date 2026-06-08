package com.example.localllmvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localllmvoice.util.findActivity

@Composable
fun OnboardingPaywallScreen(
    viewModel: PaywallViewModel,
    onPurchased: () -> Unit,
    onContinueFree: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(uiState.isEntitled) {
        if (uiState.isEntitled) onPurchased()
    }

    val body = "There's two real-world travel " +
            "scenarios for you to try completely free. If you love the " +
            "experience, you can unlock the full library later for a single, " +
            "one-time payment."

    Box(modifier = modifier.fillMaxSize()) {
        if (onContinueFree == null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                )
            }
        }

        OnboardingScaffold(
            heading = "Own it forever",
            body = body,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onContinueFree != null) {
                    OnboardingPrimaryButton(
                        text = "Get my free scenarios",
                        onClick = onContinueFree,
                    )
                }
                if (uiState.priceText != null) {
                    OnboardingPrimaryButton(
                        text = "Unlock full library: ${uiState.priceText}",
                        onClick = { activity?.let(viewModel::purchase) },
                        enabled = !uiState.isPurchasing,
                    )
                }

                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                TextButton(onClick = viewModel::restore) {
                    Text("Restore purchase")
                }
            }
        }
    }
}
