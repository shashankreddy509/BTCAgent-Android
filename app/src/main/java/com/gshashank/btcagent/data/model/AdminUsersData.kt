package com.gshashank.btcagent.data.model

/**
 * Domain aggregate for the admin Users screen — MOBILE-21.
 *
 * [pending] — users whose email is NOT in the allowlist (awaiting approval).
 * [active]  — users whose email IS in the allowlist (approved).
 *
 * The partition is client-derived in UsersRepositoryImpl; zero data.network imports here.
 */
data class AdminUsersData(
    val pending: List<AdminUser>,
    val active: List<AdminUser>,
)
