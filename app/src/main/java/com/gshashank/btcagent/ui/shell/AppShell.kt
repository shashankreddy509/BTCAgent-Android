package com.gshashank.btcagent.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gshashank.btcagent.ui.home.HomeTabScreen
import com.gshashank.btcagent.ui.markets.MarketsHubScreen
import com.gshashank.btcagent.ui.markets.stubs.AnalyticsScreen
import com.gshashank.btcagent.ui.markets.stubs.BtcRegimeScreen
import com.gshashank.btcagent.ui.markets.stubs.LiquidityMapScreen
import com.gshashank.btcagent.ui.markets.stubs.MarkovMatrixScreen
import com.gshashank.btcagent.ui.markets.stubs.OpenInterestScreen
import com.gshashank.btcagent.ui.markets.stubs.VolumeProfileScreen
import com.gshashank.btcagent.ui.markets.stubs.ZoneStrategiesScreen
import com.gshashank.btcagent.ui.navigation.HomeTab
import com.gshashank.btcagent.ui.navigation.MarketsRoute
import com.gshashank.btcagent.ui.navigation.ReportsTab
import com.gshashank.btcagent.ui.navigation.SettingsTab
import com.gshashank.btcagent.ui.navigation.TabGraph
import com.gshashank.btcagent.ui.navigation.TradeTab
import com.gshashank.btcagent.ui.positions.PositionDetailScreen
import com.gshashank.btcagent.ui.positions.PositionsListScreen
import com.gshashank.btcagent.ui.reports.ReportsScreen
import com.gshashank.btcagent.ui.settings.SettingsScreen
import com.gshashank.btcagent.ui.trade.TradeScreen

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val currentTab: TabGraph = when {
        navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(TabGraph.Markets::class) } == true -> TabGraph.Markets
        navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(TabGraph.Trade::class) } == true -> TabGraph.Trade
        navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(TabGraph.Reports::class) } == true -> TabGraph.Reports
        navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(TabGraph.Settings::class) } == true -> TabGraph.Settings
        else -> TabGraph.Home
    }

    Scaffold(
        bottomBar = {
            BtcNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    navController.navigate(tab) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TabGraph.Markets,
            modifier = Modifier.padding(innerPadding),
        ) {
            navigation<TabGraph.Home>(startDestination = HomeTab.Hub::class) {
                composable<HomeTab.Hub> {
                    HomeTabScreen(
                        onPositionsClick = {
                            navController.navigate(HomeTab.Positions)
                        },
                    )
                }
                composable<HomeTab.Positions> {
                    PositionsListScreen(
                        onPositionClick = { id ->
                            navController.navigate(HomeTab.PositionDetail(id))
                        },
                    )
                }
                composable<HomeTab.PositionDetail> { entry ->
                    val route = entry.toRoute<HomeTab.PositionDetail>()
                    PositionDetailScreen(
                        signalId = route.signalId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            navigation<TabGraph.Markets>(startDestination = MarketsRoute.Hub::class) {
                composable<MarketsRoute.Hub> {
                    MarketsHubScreen(onTileClick = { route -> navController.navigate(route) })
                }
                composable<MarketsRoute.OpenInterest> { OpenInterestScreen() }
                composable<MarketsRoute.BtcRegime> { BtcRegimeScreen() }
                composable<MarketsRoute.MarkovMatrix> { MarkovMatrixScreen() }
                composable<MarketsRoute.VolumeProfile> { VolumeProfileScreen() }
                composable<MarketsRoute.LiquidityMap> { LiquidityMapScreen() }
                composable<MarketsRoute.ZoneStrategies> { ZoneStrategiesScreen() }
                composable<MarketsRoute.Analytics> { AnalyticsScreen() }
            }
            navigation<TabGraph.Trade>(startDestination = TradeTab.Hub::class) {
                composable<TradeTab.Hub> { TradeScreen() }
            }
            navigation<TabGraph.Reports>(startDestination = ReportsTab.Hub::class) {
                composable<ReportsTab.Hub> { ReportsScreen() }
            }
            navigation<TabGraph.Settings>(startDestination = SettingsTab.Hub::class) {
                composable<SettingsTab.Hub> { SettingsScreen() }
            }
        }
    }
}
