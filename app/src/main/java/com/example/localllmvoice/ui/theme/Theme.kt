package com.example.localllmvoice.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimaryLight,
    onPrimary = TerracottaOnPrimaryLight,
    primaryContainer = TerracottaPrimaryContainerLight,
    onPrimaryContainer = TerracottaOnPrimaryContainerLight,
    inversePrimary = TerracottaInversePrimaryLight,
    secondary = SaffronSecondaryLight,
    onSecondary = SaffronOnSecondaryLight,
    secondaryContainer = SaffronSecondaryContainerLight,
    onSecondaryContainer = SaffronOnSecondaryContainerLight,
    tertiary = WarmBrownTertiaryLight,
    onTertiary = WarmBrownOnTertiaryLight,
    tertiaryContainer = WarmBrownTertiaryContainerLight,
    onTertiaryContainer = WarmBrownOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = SurfaceTintLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaPrimaryDark,
    onPrimary = TerracottaOnPrimaryDark,
    primaryContainer = TerracottaPrimaryContainerDark,
    onPrimaryContainer = TerracottaOnPrimaryContainerDark,
    inversePrimary = TerracottaInversePrimaryDark,
    secondary = SaffronSecondaryDark,
    onSecondary = SaffronOnSecondaryDark,
    secondaryContainer = SaffronSecondaryContainerDark,
    onSecondaryContainer = SaffronOnSecondaryContainerDark,
    tertiary = WarmBrownTertiaryDark,
    onTertiary = WarmBrownOnTertiaryDark,
    tertiaryContainer = WarmBrownTertiaryContainerDark,
    onTertiaryContainer = WarmBrownOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = SurfaceTintDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

@Composable
fun LocalLLMVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default to false to preserve the custom Mediterranean warm "Digital Traveler's Journal" palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
