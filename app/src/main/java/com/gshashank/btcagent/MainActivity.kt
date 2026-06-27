package com.gshashank.btcagent

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.repository.AppearanceRepository
import com.gshashank.btcagent.ui.MainViewModel
import com.gshashank.btcagent.ui.auth.LoginScreen
import com.gshashank.btcagent.ui.gate.GateScreen
import com.gshashank.btcagent.ui.navigation.Route
import com.gshashank.btcagent.ui.shell.AppShell
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appearanceRepository: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Seed with false so first composition is not blocked — updates reactively
            // when the persisted value loads from DataStore.
            val darkTheme by appearanceRepository.darkModeFlow.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val colorTheme by appearanceRepository.colorThemeFlow.collectAsStateWithLifecycle(
                initialValue = ColorTheme.BITCOIN,
            )
            BTCAgentTheme(darkTheme = darkTheme, colorTheme = colorTheme) {
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
private fun AppNavHost(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        // MOBILE-33: start past Login when a persisted Firebase session exists.
        startDestination = viewModel.startDestination,
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
            AppShell(
                onSignedOut = {
                    navController.navigate(Route.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
