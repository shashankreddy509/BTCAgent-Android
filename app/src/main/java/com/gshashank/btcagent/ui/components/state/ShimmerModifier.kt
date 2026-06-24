package com.gshashank.btcagent.ui.components.state

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Hand-rolled shimmer modifier — no external dependency.
 * Uses [rememberInfiniteTransition] to animate a horizontal sweep gradient.
 * The shimmer colours are derived from [MaterialTheme.colorScheme] (surface / surfaceVariant),
 * so the effect works in both dark and light theme.
 *
 * [drawWithContent] is used (rather than [androidx.compose.ui.draw.drawWithCache]) so the
 * animated [shimmerTranslateAnim] value is read on every draw frame and the brush sweep
 * advances correctly.
 *
 * Pattern is consistent with the pulsing-dot animation already present in GateScreen.kt.
 */
fun Modifier.shimmer(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    // 0f..1f progress; mapped to pixels per-frame from the node's own width (density-independent).
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    // Allocate the shade list once (depends only on captured theme colors), not per draw frame.
    val shimmerColorShades = listOf(
        surfaceVariantColor.copy(alpha = 0.9f),
        surfaceColor.copy(alpha = 0.3f),
        surfaceVariantColor.copy(alpha = 0.9f),
    )

    drawWithContent {
        // Sweep one bandwidth across the node width; derive from size so it scales with density.
        val band = size.width * 0.3f
        val x = shimmerProgress * (size.width + band) - band
        val brush = Brush.linearGradient(
            colors = shimmerColorShades,
            start = Offset(x, 0f),
            end = Offset(x + band, 0f),
        )
        drawContent()
        drawRect(brush = brush)
    }
}
