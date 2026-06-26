package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/volume-profiles — MOBILE-14.
 *
 * Top-level response payload.
 */
@Serializable
data class VolumeProfileDto(
    val changed: Boolean = false,
    val version: Int = 0,
    val profiles: ProfilesDto? = null,
)

/**
 * DTO for the profiles container — holds lists of snapshots per timeframe.
 */
@Serializable
data class ProfilesDto(
    @SerialName("4h") val h4: List<SnapshotDto> = emptyList(),
    @SerialName("12h") val h12: List<SnapshotDto> = emptyList(),
    @SerialName("1d") val d1: List<SnapshotDto> = emptyList(),
)

/**
 * DTO for a single volume-profile snapshot.
 */
@Serializable
data class SnapshotDto(
    val poc: Double? = null,
    val vah: Double? = null,
    @SerialName("val") val vaLow: Double? = null,
    val lo: Double? = null,
    val hi: Double? = null,
    val start: String? = null,
    val current: Boolean = false,
)
