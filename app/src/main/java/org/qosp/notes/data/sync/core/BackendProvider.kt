package org.qosp.notes.data.sync.core

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.qosp.notes.data.sync.fs.StorageBackend
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.data.sync.nextcloud.NextcloudAPIProvider
import org.qosp.notes.data.sync.nextcloud.NextcloudBackend
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.utils.ConnectionManager

class BackendProvider(
    private val context: Context,
    private val nextcloudApiProvider: NextcloudAPIProvider,
    preferenceRepository: PreferenceRepository,
    syncingScope: SyncScope,
    private val connectionManager: ConnectionManager,
) {
    private val syncService: Flow<CloudService> = preferenceRepository.getAll().map { it.cloudService }
    private val pref: StateFlow<AppPreferences?> =
        preferenceRepository.getAll().stateIn(syncingScope, SharingStarted.Eagerly, null)

    val syncProvider: StateFlow<ISyncBackend?> = combine(
        syncService,
        NextcloudConfig.fromPreferences(preferenceRepository),
        StorageConfig.storageLocation(preferenceRepository)
    ) { service, nextcloudConfig, storageConfig ->
        when (service) {
            CloudService.DISABLED -> null
            CloudService.NEXTCLOUD -> nextcloudConfig?.let { NextcloudBackend(nextcloudApiProvider, it) }
            CloudService.FILE_STORAGE -> storageConfig?.let { StorageBackend(context, it) }
        }
    }.stateIn(syncingScope, SharingStarted.Eagerly, null)

    val isSyncing: Boolean
        get() = syncProvider.value != null && connectionManager.isConnectionAvailable(
            syncMode = pref.value?.syncMode,
            cloudService = syncProvider.value?.type
        )
}
