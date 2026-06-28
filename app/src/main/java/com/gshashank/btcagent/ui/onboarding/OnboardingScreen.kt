package com.gshashank.btcagent.ui.onboarding

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Per-step icon-tile accent (orange → green → red). Header / progress bar / button stay orange. */
    val iconAccent: Color,
)

private val STEPS = listOf(
    OnboardingStep(
        title = "Monitor your bot, live",
        subtitle = "Live BTC price, open positions, and today's P&L the moment they change. " +
            "Your server-side bot — in your pocket.",
        icon = Icons.Filled.Monitor,
        iconAccent = BtcAccent,
    ),
    OnboardingStep(
        title = "Scan 1,410 timeframes",
        subtitle = "Candlestick patterns and zone strategies fire as push alerts. " +
            "DEPO marks setups forming near key levels.",
        icon = Icons.Filled.GpsFixed,
        iconAccent = BtcUp,
    ),
    OnboardingStep(
        title = "Trade behind guardrails",
        subtitle = "Paper mode by default. Live orders need Face ID and an explicit confirm — " +
            "real money always feels heavier.",
        icon = Icons.Filled.MyLocation,
        iconAccent = BtcDown,
    ),
)

// ---------------------------------------------------------------------------
// Public composable
// ---------------------------------------------------------------------------

/**
 * 3-step intro carousel shown before Login on first launch only — MOBILE-23 / MOBILE-37 (mock align).
 *
 * Matches the Claude Design onboarding mocks: a "STEP n OF 3" header + Skip top row, a centered
 * tinted icon tile (per-step colour), title + body copy, a segmented progress bar, and a full-width
 * orange "Next →" / "Get started →" button. Colours come from [MaterialTheme] so it renders correctly
 * in both Bitcoin dark and light (and any active skin).
 *
 * Stateless: navigation is driven by [onFinish], which the caller wires to persist the seen-flag and
 * navigate to Login.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { STEPS.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == STEPS.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // ---- header: STEP n OF 3  +  Skip ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STEP ${currentPage + 1} OF ${STEPS.size}",
                color = BtcAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
            )
            TextButton(onClick = onFinish) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        // ---- pager (icon tile + title + body), vertically centered ----
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            OnboardingPage(step = STEPS[page])
        }

        // ---- segmented progress bar ----
        SegmentedProgress(
            pageCount = STEPS.size,
            currentPage = currentPage,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- full-width primary button: Next → / Get started → (always orange) ----
        Button(
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BtcAccent,
                contentColor = Color.Black,
            ),
        ) {
            Text(
                text = if (isLastPage) "Get started" else "Next",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingPage(step: OnboardingStep) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Tinted rounded-square icon tile (~96dp), per-step colour.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(step.iconAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = step.title,
                tint = step.iconAccent,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = step.title,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = step.subtitle,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
}

/** Thin 3-segment progress bar, filled (orange) up to and including the current page. */
@Composable
private fun SegmentedProgress(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val inactive = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
        repeat(pageCount) { index ->
            val filled = index <= currentPage
            Box(
                modifier = Modifier
                    .weight(if (index == currentPage) 1.6f else 1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (filled) BtcAccent else inactive),
            )
        }
    }
}
