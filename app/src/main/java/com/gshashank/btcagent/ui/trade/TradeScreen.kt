package com.gshashank.btcagent.ui.trade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun TradeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_trade"),
        contentAlignment = Alignment.Center,
    ) {
        Text("Trade — coming soon")
    }
}
