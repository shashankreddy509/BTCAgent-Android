package com.gshashank.btcagent.ui.theme

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

// TODO: Replace with a proper light scheme when a light theme is designed.
private val BitcoinLightColorScheme = lightColorScheme(
    primary = BtcAccent,
    onPrimary = Color.Black,
    background = BtcBg,
    onBackground = BtcText,
    surface = BtcCard,
    onSurface = BtcText,
    surfaceVariant = BtcBg2,
    outline = BtcCardBorder,
)

@Composable
fun BTCAgentTheme(
    darkTheme: Boolean = true,
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
