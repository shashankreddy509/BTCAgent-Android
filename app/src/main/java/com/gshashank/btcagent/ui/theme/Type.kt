package com.gshashank.btcagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.gshashank.btcagent.R
import com.gshashank.btcagent.data.model.ColorTheme

// GoogleFont.Provider declared top-level to avoid re-instantiation on recomposition.
val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val RobotoFlexFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Roboto Flex"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("Roboto Flex"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
    Font(
        googleFont = GoogleFont("Roboto Flex"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.SemiBold,
    ),
    Font(
        googleFont = GoogleFont("Roboto Flex"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Bold,
    ),
)

val RobotoMonoFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Roboto Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("Roboto Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
)

/** Exposed for price displays that require tabular numerics. Alias for RobotoMonoFontFamily. */
val MonoFontFamily: FontFamily = RobotoMonoFontFamily

// =============================================================================
// Cobalt skin fonts — MOBILE-25
// =============================================================================

val SpaceGroteskFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Space Grotesk"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("Space Grotesk"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
    Font(
        googleFont = GoogleFont("Space Grotesk"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.SemiBold,
    ),
    Font(
        googleFont = GoogleFont("Space Grotesk"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Bold,
    ),
)

val IbmPlexMonoFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("IBM Plex Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("IBM Plex Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
)

// =============================================================================
// Violet skin fonts — MOBILE-25
// =============================================================================

val SoraFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Sora"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("Sora"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
    Font(
        googleFont = GoogleFont("Sora"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.SemiBold,
    ),
    Font(
        googleFont = GoogleFont("Sora"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Bold,
    ),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
    ),
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
    ),
)

// =============================================================================
// Per-skin typography helpers — MOBILE-25
// =============================================================================

/**
 * Returns a [Typography] for the given [colorTheme] skin, swapping the UI font family
 * while keeping the same size/weight scale. Pure function — safe to call from unit tests
 * (no Android runtime dependency).
 */
fun typographyFor(colorTheme: ColorTheme): Typography {
    val uiFamily = when (colorTheme) {
        ColorTheme.BITCOIN -> RobotoFlexFontFamily
        ColorTheme.COBALT -> SpaceGroteskFontFamily
        ColorTheme.VIOLET -> SoraFontFamily
    }
    return Typography(
        displayLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 45.sp,
            lineHeight = 52.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}

/**
 * Returns the mono [FontFamily] for the given [colorTheme] skin.
 * Used by [BTCAgentTheme] to provide [LocalMonoFontFamily].
 */
fun monoFamilyFor(colorTheme: ColorTheme): FontFamily = when (colorTheme) {
    ColorTheme.BITCOIN -> RobotoMonoFontFamily
    ColorTheme.COBALT -> IbmPlexMonoFontFamily
    ColorTheme.VIOLET -> JetBrainsMonoFontFamily
}
