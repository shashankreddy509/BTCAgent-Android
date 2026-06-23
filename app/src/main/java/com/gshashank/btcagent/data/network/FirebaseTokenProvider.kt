package com.gshashank.btcagent.data.network

import com.gshashank.btcagent.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed [TokenProvider].
 *
 * Calls [AuthRepository.getIdToken] (no forced refresh — the cached token is used for
 * performance on every outbound request; [TokenAuthenticator] handles the forced-refresh
 * path when the server returns 401).
 *
 * [runBlocking] is intentionally narrow — only wraps the token fetch — and is safe here
 * because OkHttp interceptors run on OkHttp's dedicated IO thread pool, not the main thread.
 */
@Singleton
class FirebaseTokenProvider @Inject constructor(
    private val authRepository: AuthRepository,
) : TokenProvider {

    override fun getToken(): String? =
        runBlocking { authRepository.getIdToken(forceRefresh = false) }.getOrNull()
}
