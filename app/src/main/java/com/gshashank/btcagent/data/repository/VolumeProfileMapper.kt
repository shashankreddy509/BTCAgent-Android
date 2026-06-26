package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Session
import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.model.VolumeProfileData
import com.gshashank.btcagent.data.network.SnapshotDto
import com.gshashank.btcagent.data.network.VolumeProfileDto

/**
 * Maps the network [VolumeProfileDto] to the domain [VolumeProfileData] — MOBILE-14.
 *
 * Lives in the data/repository layer (not data/model) so the domain model stays free of any
 * dependency on the network layer. The mapper reverses each timeframe list so that the newest
 * session (last in the API response) becomes first in the domain list.
 */
internal fun VolumeProfileDto.toDomain(): VolumeProfileData {
    val profilesDto = profiles

    fun mapList(list: List<SnapshotDto>) =
        list.reversed().map { s ->
            Session(
                start = s.start.orEmpty(),
                poc = s.poc,
                vah = s.vah,
                vaLow = s.vaLow,
                lo = s.lo,
                hi = s.hi,
            )
        }

    return VolumeProfileData(
        timeframes = mapOf(
            Timeframe.H4 to mapList(profilesDto?.h4 ?: emptyList()),
            Timeframe.H12 to mapList(profilesDto?.h12 ?: emptyList()),
            Timeframe.D1 to mapList(profilesDto?.d1 ?: emptyList()),
        ),
        version = version,
    )
}
