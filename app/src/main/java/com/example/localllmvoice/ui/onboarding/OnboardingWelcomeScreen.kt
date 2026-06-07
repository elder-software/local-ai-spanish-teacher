package com.example.localllmvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingWelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        heading = "Welcome",
        body = "Stub welcome body",
        modifier = modifier,
    ) {
        OnboardingPrimaryButton(
            text = "Start your journey",
            onClick = onContinue,
        )
    }
}
