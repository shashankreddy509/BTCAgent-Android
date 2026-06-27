package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.AdminUsersData

/**
 * Result type returned by [UsersRepository.fetchUsers] — MOBILE-21.
 */
sealed class AdminUsersResult {
    data class Success(val data: AdminUsersData) : AdminUsersResult()
    data class Error(val message: String = "") : AdminUsersResult()
}
