package com.gshashank.btcagent.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.ListRowSkeleton
import com.gshashank.btcagent.ui.components.state.StatTileSkeleton
import com.gshashank.btcagent.ui.components.state.shimmer

/**
 * Shimmer skeleton for the Dashboard Hero layout — MOBILE-39.
 *
 * Matches the structure of [DashboardHeroContent]:
 *  - Top row: two skeleton lines (title + subtitle) + RoundedBox for the PAPER/LIVE pill
 *  - Bot status: one skeleton row
 *  - Price hero card skeleton (HeroSkeleton)
 *  - Today's P&L card skeleton (StatTileSkeleton)
 *  - Open Positions card skeleton (StatTileSkeleton) + two ListRowSkeleton items
 *  - Action buttons: two side-by-side StatTileSkeleton boxes
 *
 * [testTag("dashboard_skeleton")] is applied to the root Column so instrumented tests can
 * assert that the skeleton is displayed during [UiState.Loading].
 */
@Composable
fun DashboardHeroSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_skeleton")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A. Top app-bar row skeleton: title + subtitle on left, pill on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmer()
                )
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmer()
                )
            }
            // PAPER/LIVE pill skeleton
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmer()
            )
        }

        // B. Bot status row skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .shimmer()
        )

        // C. Price hero card skeleton
        HeroSkeleton()

        // D. Today's P&L card skeleton
        StatTileSkeleton()

        // E. Open Positions card skeleton + two list row items
        StatTileSkeleton()
        ListRowSkeleton()
        ListRowSkeleton()

        // G. Action buttons row skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTileSkeleton(modifier = Modifier.weight(1f))
            StatTileSkeleton(modifier = Modifier.weight(1f))
        }
    }
}
