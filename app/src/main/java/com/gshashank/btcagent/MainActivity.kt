package com.gshashank.btcagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gshashank.btcagent.ui.auth.LoginScreen
import com.gshashank.btcagent.ui.gate.GateScreen
import com.gshashank.btcagent.ui.navigation.Route
import com.gshashank.btcagent.ui.shell.AppShell
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BTCAgentTheme {
                // Surface paints the themed background under every screen — MaterialTheme
                // only sets color tokens, it does not draw a background itself.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Login,
    ) {
        composable<Route.Login> {
            LoginScreen(
                viewModel = hiltViewModel(),
                onAuthenticated = {
                    navController.navigate(Route.Gate) {
                        popUpTo(Route.Login) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Gate> {
            GateScreen(
                viewModel = hiltViewModel(),
                onAllowed = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Gate) { inclusive = true }
                    }
                },
                onSignedOut = {
                    navController.navigate(Route.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Home> {
            AppShell()
        }
    }
}
