package com.gshashank.btcagent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.ListRowSkeleton
import com.gshashank.btcagent.ui.components.state.StatTileSkeleton

/**
 * Shimmer skeleton for the Dashboard Hero layout.
 *
 * Matches the structure of [DashboardHeroContent]:
 *  - Hero tile (live price area)
 *  - Two stat tiles (P&L and positions)
 *  - Two list rows (status + padding)
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
        HeroSkeleton()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTileSkeleton(modifier = Modifier.weight(1f))
            StatTileSkeleton(modifier = Modifier.weight(1f))
        }
        ListRowSkeleton()
        ListRowSkeleton()
    }
}
