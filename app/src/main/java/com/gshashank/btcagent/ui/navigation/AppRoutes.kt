package com.gshashank.btcagent.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe route definitions for the Navigation-Compose NavHost. */
sealed interface Route {

    @Serializable
    data object Login : Route

    @Serializable
    data object Gate : Route

    @Serializable
    data object Home : Route
}
