package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.AccessApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps [AccessApi.checkAccess] to [AccessResult].
 *
 * The endpoint returns 200 with `{allowed, admin}` for any authenticated user — the
 * verdict is the BODY, not the status code:
 *   200 + allowed=true  → [AccessResult.Allowed] (carrying admin)
 *   200 + allowed=false → [AccessResult.Pending]
 *   200 + null body     → [AccessResult.Error] (cannot confirm — never fail open)
 *   401                 → [AccessResult.Unauthorized]
 *   anything else       → [AccessResult.Error] with null cause
 *   any thrown exception → [AccessResult.Error] with non-null cause
 *
 * The gate must never fail open: only an explicit allowed=true grants access.
 */
@Singleton
class AccessRepositoryImpl @Inject constructor(
    private val accessApi: AccessApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccessRepository {

    override suspend fun checkAccess(): AccessResult = withContext(ioDispatcher) {
        try {
            val response = accessApi.checkAccess()
            when {
                response.code() == 200 -> {
                    val body = response.body()
                    when {
                        body == null -> AccessResult.Error(cause = null)
                        body.allowed -> AccessResult.Allowed(admin = body.admin)
                        else -> AccessResult.Pending
                    }
                }
                response.code() == 401 -> AccessResult.Unauthorized
                else -> AccessResult.Error(cause = null)
            }
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            AccessResult.Error(cause = e)
        }
    }
}
