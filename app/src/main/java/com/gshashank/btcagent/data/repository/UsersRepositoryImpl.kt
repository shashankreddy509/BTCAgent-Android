package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.AdminUser
import com.gshashank.btcagent.data.model.AdminUsersData
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.network.AllowlistDto
import com.gshashank.btcagent.data.network.SetModeRequest
import com.gshashank.btcagent.data.network.UsersApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [UsersRepository] — MOBILE-21.
 *
 * - Never throws to callers; rethrows [CancellationException] so coroutine cancellation propagates.
 * - Closes errorBody() on every non-2xx path to avoid connection pool starvation.
 * - HTTP error reason is masked — response.message() is never exposed to callers.
 * - 403 → "Admin access required" (user-friendly, not the raw server body "Admin only").
 * - Active = email ∈ allowlist; Pending = email ∉ allowlist (client-derived partition).
 * - approve/reject use read-modify-write: GET allowlist, mutate, PUT full list, re-fetch state.
 */
@Singleton
class UsersRepositoryImpl @Inject constructor(
    private val usersApi: UsersApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

    override suspend fun fetchUsers(): AdminUsersResult = withContext(ioDispatcher) {
        try {
            // Fetch all users
            val usersResponse = usersApi.getUsers()
            if (!usersResponse.isSuccessful) {
                usersResponse.errorBody()?.close()
                val message = if (usersResponse.code() == 403) {
                    "Admin access required"
                } else {
                    "Server error (${usersResponse.code()})"
                }
                return@withContext AdminUsersResult.Error(message)
            }
            val userDtos = usersResponse.body()
                ?: return@withContext AdminUsersResult.Error("Server error (empty body)")

            // Fetch allowlist
            val allowlistResponse = usersApi.getAllowlist()
            if (!allowlistResponse.isSuccessful) {
                allowlistResponse.errorBody()?.close()
                val message = if (allowlistResponse.code() == 403) {
                    "Admin access required"
                } else {
                    "Server error (${allowlistResponse.code()})"
                }
                return@withContext AdminUsersResult.Error(message)
            }
            val allowlistDto = allowlistResponse.body()
                ?: return@withContext AdminUsersResult.Error("Server error (empty body)")

            val allowedEmails = allowlistDto.emails.toSet()

            val domainUsers = userDtos.map { dto ->
                AdminUser(
                    uid = dto.uid,
                    email = dto.email,
                    displayName = dto.displayName,
                    mode = when (dto.mode) {
                        "live" -> ExecutionMode.LIVE
                        else -> ExecutionMode.PAPER
                    },
                    scannerRunning = dto.scannerRunning,
                )
            }

            val active = domainUsers.filter { it.email in allowedEmails }
            val pending = domainUsers.filter { it.email !in allowedEmails }

            AdminUsersResult.Success(AdminUsersData(pending = pending, active = active))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AdminUsersResult.Error("Network error")
        }
    }

    override suspend fun approve(email: String): ActionResult = withContext(ioDispatcher) {
        try {
            // Read-modify-write: GET current allowlist
            val getResponse = usersApi.getAllowlist()
            if (!getResponse.isSuccessful) {
                getResponse.errorBody()?.close()
                return@withContext errorResult(getResponse.code())
            }
            val currentEmails = getResponse.body()?.emails ?: emptyList()

            // Add email if not already present
            val updatedEmails = if (email in currentEmails) currentEmails else currentEmails + email

            // PUT the updated list
            val putResponse = usersApi.putAllowlist(AllowlistDto(emails = updatedEmails))
            if (!putResponse.isSuccessful) {
                putResponse.errorBody()?.close()
                return@withContext errorResult(putResponse.code())
            }
            putResponse.body() // consume the 2xx body so the connection returns to the pool
            // State refresh is the ViewModel's job (refresh() after success) — no extra fetch here.
            ActionResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Network error")
        }
    }

    override suspend fun reject(email: String): ActionResult = withContext(ioDispatcher) {
        try {
            // Read-modify-write: GET current allowlist
            val getResponse = usersApi.getAllowlist()
            if (!getResponse.isSuccessful) {
                getResponse.errorBody()?.close()
                return@withContext errorResult(getResponse.code())
            }
            val currentEmails = getResponse.body()?.emails ?: emptyList()

            // Remove email
            val updatedEmails = currentEmails.filter { it != email }

            // PUT the updated list
            val putResponse = usersApi.putAllowlist(AllowlistDto(emails = updatedEmails))
            if (!putResponse.isSuccessful) {
                putResponse.errorBody()?.close()
                return@withContext errorResult(putResponse.code())
            }
            putResponse.body() // consume the 2xx body
            // State refresh is the ViewModel's job after success — no extra fetch here.
            ActionResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Network error")
        }
    }

    override suspend fun setMode(uid: String, mode: ExecutionMode): ActionResult = withContext(ioDispatcher) {
        try {
            val modeString = when (mode) {
                ExecutionMode.LIVE -> "live"
                ExecutionMode.PAPER -> "paper"
            }
            val response = usersApi.setMode(uid, SetModeRequest(mode = modeString))
            if (response.isSuccessful) {
                response.body() // consume the 2xx body
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                errorResult(response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Network error")
        }
    }

    override suspend fun stop(uid: String): ActionResult = withContext(ioDispatcher) {
        try {
            val response = usersApi.stop(uid)
            if (response.isSuccessful) {
                response.body() // consume the 2xx body
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                errorResult(response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Network error")
        }
    }

    private fun errorResult(code: Int): ActionResult.Error = when (code) {
        403 -> ActionResult.Error(code = 403, message = "Admin access required")
        else -> ActionResult.Error(code = code, message = "Server error ($code)")
    }
}
