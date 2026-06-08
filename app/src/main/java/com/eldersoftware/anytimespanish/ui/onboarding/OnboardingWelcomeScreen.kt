package com.eldersoftware.anytimespanish.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingWelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        heading = "Welcome to Anytime Spanish",
        body = "Your private, always-ready language companion. Practice " +
            "speaking 100% offline. No subscriptions, no cloud servers, " +
            "no judgment.",
        modifier = modifier,
    ) {
        OnboardingPrimaryButton(
            text = "Start your journey",
            onClick = onContinue,
        )
    }
}
