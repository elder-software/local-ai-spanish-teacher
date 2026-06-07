package com.example.localllmvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingDownloadScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        heading = "Downloading",
        body = "Stub downloading body",
        modifier = modifier,
    ) {
        OnboardingPrimaryButton(
            text = "Start talking",
            onClick = onFinished,
        )
    }
}
