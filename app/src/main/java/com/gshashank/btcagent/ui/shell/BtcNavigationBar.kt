package com.gshashank.btcagent.ui.shell

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gshashank.btcagent.ui.navigation.TabGraph

private data class TabItem(
    val tab: TabGraph,
    val label: String,
    val icon: String,
    val testTag: String,
)

private val tabItems = listOf(
    TabItem(TabGraph.Home, "Home", "🏠", "tab_home"),
    TabItem(TabGraph.Markets, "Markets", "📊", "tab_markets"),
    TabItem(TabGraph.Trade, "Trade", "💹", "tab_trade"),
    TabItem(TabGraph.Reports, "Reports", "📋", "tab_reports"),
    TabItem(TabGraph.Settings, "Settings", "⚙️", "tab_settings"),
)

@Composable
fun BtcNavigationBar(
    currentTab: TabGraph,
    onTabSelected: (TabGraph) -> Unit,
) {
    NavigationBar {
        tabItems.forEach { item ->
            NavigationBarItem(
                selected = currentTab == item.tab,
                onClick = { onTabSelected(item.tab) },
                icon = { Text(text = item.icon) },
                label = { Text(text = item.label) },
                modifier = Modifier.testTag(item.testTag),
            )
        }
    }
}
