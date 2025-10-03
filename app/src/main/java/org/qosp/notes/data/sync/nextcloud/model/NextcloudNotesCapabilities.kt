package org.qosp.notes.data.sync.nextcloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NextcloudNotesCapabilities(
    @SerialName("api_version")
    val apiVersion: List<String>,
    val version: String,
    @SerialName("notes_path")
    val notesPath: String? = null,
)

@Serializable
data class NextcloudCapabilitiesResult(
    val ocs: NextcloudCapabilitiesResultOcs
)

@Serializable
data class NextcloudCapabilitiesResultOcs(
    val data: NextcloudCapabilitiesResultData
)

@Serializable
data class NextcloudCapabilitiesResultData(
    val capabilities: NextcloudCapabilitiesResultCapabilities
)

@Serializable
data class NextcloudCapabilitiesResultCapabilities(
    val notes: NextcloudNotesCapabilities? = null
)
