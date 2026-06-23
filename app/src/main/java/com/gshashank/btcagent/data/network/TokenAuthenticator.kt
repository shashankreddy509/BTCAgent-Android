package com.gshashank.btcagent.data.network

import com.gshashank.btcagent.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retries a 401 response exactly once with a force-refreshed Firebase ID token.
 * Gives up (returns null) immediately when [Response.priorResponse] is non-null to
 * prevent an infinite retry loop.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authRepository: AuthRepository,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // If we've already retried once, give up to prevent an infinite loop.
        if (response.priorResponse != null) {
            return null
        }

        // runBlocking is intentionally narrow — only wraps the token fetch.
        // forceRefresh=true: the cached token just got rejected, so refresh from the network.
        val tokenResult = runBlocking { authRepository.getIdToken(forceRefresh = true) }
        val token = tokenResult.getOrNull() ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
