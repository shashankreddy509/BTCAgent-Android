package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.AccessApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps HTTP status codes from [AccessApi.checkAccess] to [AccessResult] variants.
 *
 * 200 → [AccessResult.Allowed]
 * 403 → [AccessResult.Pending]
 * 401 → [AccessResult.Unauthorized]
 * anything else → [AccessResult.Error] with null cause
 * any thrown exception (IO, serialization, etc.) → [AccessResult.Error] with non-null cause
 *
 * The gate must never fail open: every non-200 status and every thrown exception
 * resolves to Error/Unauthorized, never Allowed.
 */
@Singleton
class AccessRepositoryImpl @Inject constructor(
    private val accessApi: AccessApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccessRepository {

    override suspend fun checkAccess(): AccessResult = withContext(ioDispatcher) {
        try {
            val response = accessApi.checkAccess()
            when (response.code()) {
                200 -> AccessResult.Allowed
                403 -> AccessResult.Pending
                401 -> AccessResult.Unauthorized
                else -> AccessResult.Error(cause = null)
            }
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            AccessResult.Error(cause = e)
        }
    }
}
