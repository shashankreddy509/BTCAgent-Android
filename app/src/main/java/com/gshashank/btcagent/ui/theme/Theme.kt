package com.gshashank.btcagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.gshashank.btcagent.data.model.ColorTheme

/** CompositionLocal providing the active skin's mono/number font family. */
val LocalMonoFontFamily = staticCompositionLocalOf<FontFamily> { MonoFontFamily }

// =============================================================================
// Bitcoin color schemes
// =============================================================================

private val BitcoinDarkColorScheme = darkColorScheme(
    primary = BtcAccent,
    onPrimary = Color.Black,
    background = BtcBg,
    onBackground = BtcText,
    surface = BtcCard,
    onSurface = BtcText,
    surfaceVariant = BtcBg2,
    outline = BtcCardBorder,
)

private val BitcoinLightColorScheme = lightColorScheme(
    primary = BtcAccent,
    onPrimary = Color.White,
    background = BtcLightBg,
    onBackground = BtcLightText,
    surface = BtcLightCard,
    onSurface = BtcLightText,
    surfaceVariant = BtcLightBg2,
    outline = BtcLightCardBorder,
)

// =============================================================================
// Cobalt color schemes — MOBILE-25
// =============================================================================

internal val CobaltDarkColorScheme = darkColorScheme(
    primary = CobaltAccent,
    onPrimary = Color.Black,
    background = CobaltDarkBg,
    onBackground = CobaltDarkText,
    surface = CobaltDarkCard,
    onSurface = CobaltDarkText,
    surfaceVariant = CobaltDarkBg2,
    outline = CobaltDarkCardBorder,
)

internal val CobaltLightColorScheme = lightColorScheme(
    primary = CobaltAccent,
    onPrimary = Color.White,
    background = CobaltLightBg,
    onBackground = CobaltLightText,
    surface = CobaltLightCard,
    onSurface = CobaltLightText,
    surfaceVariant = CobaltLightBg2,
    outline = CobaltLightCardBorder,
)

// =============================================================================
// Violet color schemes — MOBILE-25
// =============================================================================

internal val VioletDarkColorScheme = darkColorScheme(
    primary = VioletAccent,
    onPrimary = Color.Black,
    background = VioletDarkBg,
    onBackground = VioletDarkText,
    surface = VioletDarkCard,
    onSurface = VioletDarkText,
    surfaceVariant = VioletDarkBg2,
    outline = VioletDarkCardBorder,
)

internal val VioletLightColorScheme = lightColorScheme(
    primary = VioletAccent,
    onPrimary = Color.White,
    background = VioletLightBg,
    onBackground = VioletLightText,
    surface = VioletLightCard,
    onSurface = VioletLightText,
    surfaceVariant = VioletLightBg2,
    outline = VioletLightCardBorder,
)

@Composable
fun BTCAgentTheme(
    // Default follows the system setting; design is dark-first but both schemes are real.
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.BITCOIN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (colorTheme) {
        ColorTheme.BITCOIN -> if (darkTheme) BitcoinDarkColorScheme else BitcoinLightColorScheme
        ColorTheme.COBALT -> if (darkTheme) CobaltDarkColorScheme else CobaltLightColorScheme
        ColorTheme.VIOLET -> if (darkTheme) VioletDarkColorScheme else VioletLightColorScheme
    }
    // remember keyed on colorTheme so the Typography/mono families aren't rebuilt on every
    // unrelated root recomposition (only when the skin actually changes).
    val typography = remember(colorTheme) { typographyFor(colorTheme) }
    val monoFamily = remember(colorTheme) { monoFamilyFor(colorTheme) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(LocalMonoFontFamily provides monoFamily) {
            content()
        }
    }
}
