package org.qosp.notes.data.sync.nextcloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NextcloudCapabilities(
    @SerialName("api_version")
    val apiVersion: List<String>,
    val version: String,
)
