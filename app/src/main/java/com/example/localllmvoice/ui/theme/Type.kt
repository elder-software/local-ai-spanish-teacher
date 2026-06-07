package com.example.localllmvoice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.localllmvoice.R

// Downloadable fonts configuration
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Libre Caslon Text FontFamily for headers (Updated June 2026 for literary elegance)
private val LibreCaslonTextFont = GoogleFont("Libre Caslon Text")
val LibreCaslonTextFontFamily = FontFamily(
    Font(googleFont = LibreCaslonTextFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = LibreCaslonTextFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = LibreCaslonTextFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = LibreCaslonTextFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

// DM Sans FontFamily for body text
private val DmSansFont = GoogleFont("DM Sans")
val DmSansFontFamily = FontFamily(
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

// Set of Material typography styles based on design.md
val Typography = Typography(
    // displayLarge maps to display-lg in design.md
    displayLarge = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.01).em
    ),
    // displayMedium maps to display-lg-mobile in design.md
    displayMedium = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.01).em
    ),
    // displaySmall as a scale fallback
    displaySmall = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em
    ),
    // headlineLarge
    headlineLarge = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    // headlineMedium maps to headline-md in design.md
    headlineMedium = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    // headlineSmall
    headlineSmall = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    // titleLarge for standard large titles
    titleLarge = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // titleMedium for cards/subheadings (used in Dashboard/Feedback)
    titleMedium = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // titleSmall (used in Dashboard)
    titleSmall = TextStyle(
        fontFamily = LibreCaslonTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // bodyLarge maps to body-lg in design.md
    bodyLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 30.sp
    ),
    // bodyMedium maps to body-md in design.md
    bodyMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    // bodySmall (used in Dashboard)
    bodySmall = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // labelLarge (used in ChatScreen)
    labelLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.em
    ),
    // labelMedium (used in ChatScreen, Dashboard)
    labelMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.em
    ),
    // labelSmall maps to label-sm in design.md
    labelSmall = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.em
    )
)
