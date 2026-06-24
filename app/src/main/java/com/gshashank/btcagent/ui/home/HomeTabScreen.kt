package com.gshashank.btcagent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun HomeTabScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_home"),
        contentAlignment = Alignment.Center,
    ) {
        Text("Home — coming soon")
    }
}
