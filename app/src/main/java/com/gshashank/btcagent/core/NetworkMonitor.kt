package com.gshashank.btcagent.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity and exposes it as [isOnlineFlow].
 *
 * Registers a [ConnectivityManager.NetworkCallback] for INTERNET capability.
 * The initial value reflects the current connectivity state at construction time.
 *
 * Requires [android.Manifest.permission.ACCESS_NETWORK_STATE] in AndroidManifest.xml.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnlineFlow = MutableStateFlow(isCurrentlyOnline())

    /** Emits `true` when at least one network with INTERNET capability is available. */
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnlineFlow.value = true
        }

        override fun onLost(network: Network) {
            // Re-evaluate: another network may still be available.
            _isOnlineFlow.value = isCurrentlyOnline()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // A missing ACCESS_NETWORK_STATE permission throws SecurityException; degrade to the
        // construction-time snapshot rather than crashing the app.
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: SecurityException) {
            // No live updates; isOnlineFlow keeps its initial value.
        }
    }

    /**
     * Unregisters the callback. Not normally called (this is an app-lifetime @Singleton that dies
     * with the process), but provided for completeness / testability and idempotent on double-call.
     */
    fun close() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            // Already unregistered — ignore.
        }
    }

    private fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
