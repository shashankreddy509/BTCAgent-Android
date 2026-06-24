package com.gshashank.btcagent.data.network

import kotlinx.serialization.Serializable

@Serializable
data class CatalogResponse(
    val changed: Boolean,
    val version: Int,
    val catalogs: Map<String, Boolean>? = null
)
