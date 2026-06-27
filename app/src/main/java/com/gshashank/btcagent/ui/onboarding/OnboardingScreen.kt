package com.gshashank.btcagent.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcDown
import com.gshashank.btcagent.ui.theme.BtcUp
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

private data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
)

private val STEPS = listOf(
    OnboardingStep(
        title = "Monitor your bot, live",
        subtitle = "Live prices, P&L, and positions at a glance.",
        icon = Icons.Filled.Monitor,
        accent = BtcAccent,
    ),
    OnboardingStep(
        title = "Scan 1,410 timeframes",
        subtitle = "Pattern alerts and DEPO setups across every timeframe.",
        icon = Icons.Filled.Search,
        accent = BtcUp,
    ),
    OnboardingStep(
        title = "Trade behind guardrails",
        subtitle = "Paper mode by default. Face ID required for live trading.",
        icon = Icons.Filled.Shield,
        accent = BtcDown,
    ),
)

// ---------------------------------------------------------------------------
// Public composable
// ---------------------------------------------------------------------------

/**
 * 3-step intro carousel shown before Login on first launch only — MOBILE-23.
 *
 * Stateless: all navigation is driven by the [onFinish] callback, which the caller wires
 * to persist the seen-flag and navigate to Login.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { STEPS.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ---- pager ----
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val step = STEPS[page]
            OnboardingPage(step = step)
        }

        // ---- progress dots ----
        val currentAccent = STEPS[pagerState.currentPage].accent
        ProgressDots(
            pageCount = STEPS.size,
            currentPage = pagerState.currentPage,
            activeAccent = currentAccent,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- button row ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Skip — hidden on last page
            if (pagerState.currentPage < STEPS.size - 1) {
                TextButton(onClick = onFinish) {
                    Text(text = "Skip")
                }
            } else {
                Spacer(modifier = Modifier) // zero-width; keeps SpaceBetween right-aligning the button
            }

            // Next / Get started
            val isLastPage = pagerState.currentPage == STEPS.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentAccent,
                ),
            ) {
                Text(text = if (isLastPage) "Get started" else "Next")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingPage(step: OnboardingStep) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 96dp icon tile
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(step.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = step.title,
                tint = step.accent,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = step.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ProgressDots(
    pageCount: Int,
    currentPage: Int,
    activeAccent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                label = "dot_width_$index",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (isActive) activeAccent
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}
