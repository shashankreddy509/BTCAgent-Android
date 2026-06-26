package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.VolumeProfileApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [VolumeProfileRepository] — MOBILE-14.
 *
 * Calls [VolumeProfileApi.get] and maps the DTO to domain models:
 *  - HTTP non-2xx      → [VolumeProfileResult.Error] with "HTTP {code}".
 *  - Null body         → [VolumeProfileResult.Error] with "Empty body".
 *  - Empty profiles    → [VolumeProfileResult.Success] with [isEmpty] == true.
 *  - [CancellationException] is rethrown; never throws to callers.
 */
@Singleton
class VolumeProfileRepositoryImpl @Inject constructor(
    private val api: VolumeProfileApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VolumeProfileRepository {

    override suspend fun fetch(): VolumeProfileResult = withContext(ioDispatcher) {
        try {
            val r = api.get()
            if (!r.isSuccessful) {
                r.errorBody()?.close()
                return@withContext VolumeProfileResult.Error("HTTP ${r.code()}")
            }
            val body = r.body() ?: run {
                r.errorBody()?.close()
                return@withContext VolumeProfileResult.Error("Empty body")
            }
            // v1: always fetches fresh; the `changed`/`version` envelope is deserialized but
            // version-cache dedup is intentionally deferred (this screen opens occasionally).
            VolumeProfileResult.Success(body.toDomain())
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            // Don't surface raw exception text (may carry host/IP); keep a generic message.
            VolumeProfileResult.Error("Network error")
        }
    }
}
