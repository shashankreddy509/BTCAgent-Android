package com.gshashank.btcagent.ui.markets.volume_profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.Session
import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.model.VolumeProfileData
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Stateless content composable — test seam for Volume Profile screen — MOBILE-14.
 *
 * Maps [UiState<VolumeProfileData>] to the appropriate UI exhaustively:
 *  - Loading  → [HeroSkeleton] (testTag "vp_loading")
 *  - Empty    → [EmptyStateContent] (testTag "vp_empty")
 *  - Error    → error message + Retry button (testTag "vp_error")
 *  - Offline  → offline message + Retry button (testTag "vp_offline")
 *  - Ready    → content with timeframe chips + sessions (testTag "vp_ready")
 */
@Composable
fun VolumeProfileScreenContent(
    uiState: UiState<VolumeProfileData>,
    selectedTimeframe: Timeframe,
    onSelectTimeframe: (Timeframe) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vp_loading"),
                ) {
                    HeroSkeleton()
                }
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("vp_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("vp_error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.message)
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Offline -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vp_offline"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "You are offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vp_ready"),
                ) {
                    // Timeframe chips
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        items(Timeframe.entries) { tf ->
                            FilterChip(
                                selected = tf == selectedTimeframe,
                                onClick = { onSelectTimeframe(tf) },
                                label = { Text(tf.key) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }

                    val sessions = uiState.data.timeframes[selectedTimeframe] ?: emptyList()
                    if (sessions.isEmpty()) {
                        Text(
                            text = "No session data",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(sessions) { session ->
                                SessionRow(session = session)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single volume-profile session row.
 *
 * Shows the session start label, a [PriceLadder] Canvas, and a value-area caption.
 * If [Session.hasData] is false, shows "No session data" text instead.
 */
@Composable
internal fun SessionRow(session: Session, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = session.start,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!session.hasData) {
            Text(
                text = "No session data",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            PriceLadder(
                session = session,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(top = 4.dp),
            )
            Text(
                // Safe: this branch is gated by session.hasData (all profile fields non-null).
                text = "Value area: ${"%.1f".format(session.vaLow!!)}–${"%.1f".format(session.vah!!)}, POC ${"%.1f".format(session.poc!!)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Canvas-based vertical price ladder for a single [Session].
 *
 * Draws:
 * - A shaded value-area band between [Session.vaLow] and [Session.vah].
 * - hi and lo bounds as muted horizontal lines.
 * - POC as a primary-color line.
 *
 * Colors are captured from [MaterialTheme] BEFORE the Canvas lambda to avoid
 * reading composition-locals inside the draw scope.
 */
@Composable
internal fun PriceLadder(session: Session, modifier: Modifier = Modifier) {
    // Capture theme colors before the Canvas lambda (CONTRAST-FIXED pattern).
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val lo = session.lo ?: return@Canvas
        val hi = session.hi ?: return@Canvas
        val vaLow = session.vaLow ?: return@Canvas
        val vah = session.vah ?: return@Canvas
        val poc = session.poc ?: return@Canvas

        if (hi <= lo) return@Canvas

        val canvasHeight = size.height
        val canvasWidth = size.width
        val range = hi - lo

        // Helper: convert a price to a Y-pixel position (top = hi, bottom = lo).
        fun priceToY(price: Double): Float =
            ((hi - price) / range * canvasHeight).toFloat()

        // Shade value-area band.
        val vaLowY = priceToY(vaLow)
        val vahY = priceToY(vah)
        drawRect(
            color = primaryColor.copy(alpha = 0.12f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, vahY),
            size = androidx.compose.ui.geometry.Size(canvasWidth, vaLowY - vahY),
        )

        // Hi bound line.
        drawLine(
            color = onSurfaceVariantColor.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(0f, priceToY(hi)),
            end = androidx.compose.ui.geometry.Offset(canvasWidth, priceToY(hi)),
            strokeWidth = 1.dp.toPx(),
        )

        // Lo bound line.
        drawLine(
            color = onSurfaceVariantColor.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(0f, priceToY(lo)),
            end = androidx.compose.ui.geometry.Offset(canvasWidth, priceToY(lo)),
            strokeWidth = 1.dp.toPx(),
        )

        // POC line.
        drawLine(
            color = primaryColor,
            start = androidx.compose.ui.geometry.Offset(0f, priceToY(poc)),
            end = androidx.compose.ui.geometry.Offset(canvasWidth, priceToY(poc)),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
