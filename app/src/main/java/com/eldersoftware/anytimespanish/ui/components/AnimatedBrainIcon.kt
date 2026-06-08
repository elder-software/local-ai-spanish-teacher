package com.eldersoftware.anytimespanish.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.lerp

@Composable
fun AnimatedBrainIcon(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "animated_brain")
    val brainScale = transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "animated_brain_scale",
    ).value
    val shimmerProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "animated_brain_shimmer",
    ).value
    val brainTint = lerp(
        start = MaterialTheme.colorScheme.primary,
        stop = MaterialTheme.colorScheme.secondaryContainer,
        fraction = shimmerProgress,
    )

    Icon(
        imageVector = Icons.Outlined.Psychology,
        contentDescription = null,
        tint = brainTint,
        modifier = modifier.scale(brainScale),
    )
}
