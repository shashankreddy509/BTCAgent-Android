package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.LiquidityApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [LiquidityRepository] — MOBILE-15.
 *
 * Calls [LiquidityApi.getLiquidity] and maps the DTO to domain models:
 *  - HTTP 403              → [LiquidityResult.Forbidden].
 *  - HTTP non-2xx          → [LiquidityResult.Error] with "HTTP {code}".
 *  - Null body             → [LiquidityResult.Error] with "Empty body".
 *  - Empty rows / no_data  → [LiquidityResult.Success] with [isEmpty] == true.
 *  - [CancellationException] is rethrown; never throws to callers.
 */
@Singleton
class LiquidityRepositoryImpl @Inject constructor(
    private val liquidityApi: LiquidityApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LiquidityRepository {

    override suspend fun fetch(): LiquidityResult = withContext(ioDispatcher) {
        try {
            val response = liquidityApi.getLiquidity()
            if (response.code() == 403) {
                response.errorBody()?.close()
                return@withContext LiquidityResult.Forbidden
            }
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext LiquidityResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext LiquidityResult.Error(message = "Empty body")
                }

            LiquidityResult.Success(body.toDomain())
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            // Don't surface raw exception text (may carry host/IP); keep a generic message.
            LiquidityResult.Error(message = "Network error")
        }
    }
}
