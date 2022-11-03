package org.qosp.notes.data.sync.core

import org.qosp.notes.preferences.CloudService

interface ProviderConfig {
    val remoteAddress: String
    val username: String
    val provider: CloudService
    val authenticationHeaders: Map<String, String>
}
