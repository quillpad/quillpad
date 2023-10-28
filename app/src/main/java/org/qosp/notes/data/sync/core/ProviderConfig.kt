package org.qosp.notes.data.sync.core

import org.qosp.notes.preferences.CloudService

interface ProviderConfig {
    val provider: CloudService
}
