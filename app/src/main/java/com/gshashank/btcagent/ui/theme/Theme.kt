package com.gshashank.btcagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun BTCAgentTheme(
    // Default follows the system setting; design is dark-first but both schemes are real.
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) BitcoinDarkColorScheme else BitcoinLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
