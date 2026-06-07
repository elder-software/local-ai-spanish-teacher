package com.example.localllmvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingPaywallScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        heading = "Paywall",
        body = "Stub paywall body",
        modifier = modifier,
    ) {
        OnboardingPrimaryButton(
            text = "Get my free scenarios",
            onClick = onContinue,
        )
    }
}
