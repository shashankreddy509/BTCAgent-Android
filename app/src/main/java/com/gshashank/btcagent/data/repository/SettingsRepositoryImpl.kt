package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.UserSettings
import com.gshashank.btcagent.data.network.SettingsApi
import com.gshashank.btcagent.data.network.UserSettingsWriteRequest
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [SettingsRepository] — MOBILE-20.
 *
 * - Never throws to callers; rethrows [CancellationException] so coroutine cancellation propagates.
 * - Closes errorBody() on every non-2xx path to avoid connection pool starvation.
 * - HTTP error reason is masked as "Server error (<code>)" — response.message() is never exposed.
 * - qty validation: 0 < qty <= 1000 AND even; invalid → immediate ActionResult.Error, no HTTP call.
 * - MASKED-KEY GUARD: mode is an enum at the call site, so it cannot contain "****".
 *   UserSettingsWriteRequest has no broker_keys field, so masked display strings are structurally
 *   excluded from the PUT body.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    val settingsApi: SettingsApi,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    override suspend fun fetchUserSettings(): SettingsResult = withContext(ioDispatcher) {
        try {
            val response = settingsApi.getUserSettings()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext SettingsResult.Error("Server error (${response.code()})")
            }
            val body = response.body()
                ?: run {
                    return@withContext SettingsResult.Error("Server error (empty body)")
                }

            val mode = when (body.mode) {
                "live" -> ExecutionMode.LIVE
                "paper" -> ExecutionMode.PAPER
                else -> null
            }

            SettingsResult.Success(
                UserSettings(
                    qty = body.qty,
                    maxSl = body.maxSl,
                    minTp = body.minTp,
                    maxConcurrent = body.maxConcurrent,
                    mode = mode,
                    brokerKeys = body.brokerKeys,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SettingsResult.Error("Network error")
        }
    }

    override suspend fun saveTradingParams(
        qty: Int?,
        maxSl: Double?,
        minTp: Double?,
        maxConcurrent: Int?,
        mode: ExecutionMode?,
    ): ActionResult {
        // Client-side qty validation: must be > 0, <= 1000, and even.
        // Invalid qty → immediate error with no HTTP call.
        if (qty != null && (qty <= 0 || qty > 1000 || qty % 2 != 0)) {
            return ActionResult.Error(
                code = 422,
                message = "Invalid qty: must be even and 0 < qty <= 1000",
            )
        }

        return withContext(ioDispatcher) {
            try {
                // MASKED-KEY GUARD: mode is an enum — cannot contain "****".
                // UserSettingsWriteRequest has no broker_keys field, so masked display strings
                // returned by the server are structurally excluded from the PUT body.
                val modeString = when (mode) {
                    ExecutionMode.LIVE -> "live"
                    ExecutionMode.PAPER -> "paper"
                    null -> null
                }

                val request = UserSettingsWriteRequest(
                    qty = qty,
                    maxSl = maxSl,
                    minTp = minTp,
                    maxConcurrent = maxConcurrent,
                    mode = modeString,
                )

                val response = settingsApi.saveUserSettings(request)
                if (response.isSuccessful) {
                    ActionResult.Success
                } else {
                    response.errorBody()?.close()
                    ActionResult.Error(
                        code = response.code(),
                        message = "Server error (${response.code()})",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionResult.Error(code = -1, message = "Network error")
            }
        }
    }
}
