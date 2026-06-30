package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.BrokerInfo
import com.gshashank.btcagent.data.network.BrokerApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [BrokerRepository] — MOBILE-45.
 *
 * - Never throws to callers; rethrows [CancellationException] so coroutine cancellation propagates.
 * - Closes errorBody() on every non-2xx path to avoid connection pool starvation.
 * - [SerializationException] is caught distinctly from transport exceptions.
 * - CRITICAL: [BrokerInfo.connected] null is preserved as-is — it is NOT coerced to false.
 *   null = not probed (neutral); false = probed and disconnected. These are distinct states.
 * - The PUT body uses dynamic broker-prefixed keys (e.g. "coinbase_api_key") and never
 *   sends "api_key_masked".
 */
@Singleton
class BrokerRepositoryImpl @Inject constructor(
    private val brokerApi: BrokerApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BrokerRepository {

    override suspend fun fetchBroker(): BrokerResult = withContext(ioDispatcher) {
        try {
            val response = brokerApi.getBroker()
            if (response.isSuccessful) {
                // Safe: isSuccessful guarantees body is non-null for a successful response,
                // but we guard with ?: to avoid !! and satisfy the "no !!" rule.
                val dto = response.body() ?: return@withContext BrokerResult.Error("Empty response body")

                if (dto.brokers.isEmpty()) {
                    return@withContext BrokerResult.Success(brokerInfo = null)
                }

                // Prefer the row with active=true; fall back to the first row.
                val activeRow = dto.brokers.firstOrNull { it.active }
                    ?: dto.brokers.firstOrNull()

                val brokerInfo = activeRow?.let { row ->
                    BrokerInfo(
                        broker = row.broker ?: "",
                        accountName = row.accountName ?: "",
                        apiKeyMasked = row.apiKeyMasked ?: "",
                        connected = row.connected,  // preserve null as-is
                    )
                }
                BrokerResult.Success(brokerInfo = brokerInfo)
            } else {
                val errorMsg = if (response.code() == 403) "Access not approved" else "HTTP ${response.code()}"
                response.errorBody()?.close()
                BrokerResult.Error(message = errorMsg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            BrokerResult.Error(message = "Response parse error: ${e.message}")
        } catch (e: Exception) {
            BrokerResult.Error(message = e.message ?: "Unknown error")
        }
    }

    override suspend fun replaceBroker(
        broker: String,
        apiKey: String,
        apiSecret: String,
    ): BrokerActionResult = withContext(ioDispatcher) {
        try {
            val body = mapOf(
                "broker" to broker,
                "${broker}_api_key" to apiKey,
                "${broker}_api_secret" to apiSecret,
            )
            val response = brokerApi.saveBroker(body)
            if (response.isSuccessful) {
                BrokerActionResult.Success
            } else {
                val errorMsg = if (response.code() == 403) "Access not approved" else "HTTP ${response.code()}"
                response.errorBody()?.close()
                BrokerActionResult.Error(message = errorMsg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            BrokerActionResult.Error(message = "Response parse error: ${e.message}")
        } catch (e: Exception) {
            BrokerActionResult.Error(message = e.message ?: "Unknown error")
        }
    }
}
