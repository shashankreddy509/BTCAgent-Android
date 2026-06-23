package com.gshashank.btcagent.data.network

/**
 * Minimal interface — returns the current Firebase ID token synchronously.
 * Called from OkHttp interceptor threads (synchronous context); suspend is not allowed here.
 * Implementation fills in real logic; this stub exists only so the test file compiles.
 */
interface TokenProvider {
    fun getToken(): String?
}
