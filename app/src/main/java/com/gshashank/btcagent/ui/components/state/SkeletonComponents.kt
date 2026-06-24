package com.gshashank.btcagent.ui.components.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Tall rectangle skeleton representing a hero price tile (e.g. BTC current price). */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmer()
    )
}

/** Short rectangle skeleton representing a stat tile; typically drawn twice in a Row. */
@Composable
fun StatTileSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmer()
    )
}

/**
 * Full-width bar skeleton with a circle placeholder on the left.
 * Represents a list row item (e.g. a market pair row).
 */
@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .shimmer()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .shimmer()
        )
    }
}

/**
 * Composed skeleton layout: hero tile + two stat tiles in a row + N list rows.
 * This is the default skeleton rendered by [DataStateScaffold] for [UiState.Loading].
 *
 * @param rowCount Number of [ListRowSkeleton] items to show (default 6).
 */
@Composable
fun DataLoadingSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 6,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scaffold_skeleton")
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
        repeat(rowCount) {
            ListRowSkeleton()
        }
    }
}
