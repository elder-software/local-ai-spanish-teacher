package com.example.localllmvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingPaywallScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        heading = "Own it forever",
        body = "We hate subscriptions. We’ve unlocked two real-world travel " +
            "scenarios for you to try completely free. If you love the " +
            "experience, you can unlock the full library later for a single " +
            "one-time payment of \$29.99.",
        modifier = modifier,
    ) {
        OnboardingPrimaryButton(
            text = "Get my free scenarios",
            onClick = onContinue,
        )
    }
}
