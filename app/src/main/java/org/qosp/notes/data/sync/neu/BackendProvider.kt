package org.qosp.notes.data.sync.neu

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Named
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.di.SYNC_SCOPE
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.CloudService.DISABLED
import org.qosp.notes.preferences.CloudService.FILE_STORAGE
import org.qosp.notes.preferences.CloudService.NEXTCLOUD
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.utils.ConnectionManager

class BackendProvider(
    private val context: Context,
    private val nextcloudApi: NextcloudAPI,
    preferenceRepository: PreferenceRepository,
    @Named(SYNC_SCOPE) syncingScope: CoroutineScope,
    private val connectionManager: ConnectionManager,
) {
    private val syncService: Flow<CloudService> = preferenceRepository.getAll().map { it.cloudService }
    private val pref: StateFlow<AppPreferences?> =
        preferenceRepository.getAll().stateIn(syncingScope, SharingStarted.Eagerly, null)

    val syncProvider: StateFlow<INewSyncBackend?> = combine(
        syncService,
        NextcloudConfig.fromPreferences(preferenceRepository),
        StorageConfig.storageLocation(preferenceRepository)
    ) { service, nextcloudConfig, storageConfig ->
        when (service) {
            DISABLED -> null
            NEXTCLOUD -> nextcloudConfig?.let { NewNextcloudBackend(nextcloudApi, it) }
            FILE_STORAGE -> storageConfig?.let { NewStorageBackend(context, it) }
        }
    }.stateIn(syncingScope, SharingStarted.Eagerly, null)

    val isSyncing: Boolean
        get() = syncProvider.value != null && connectionManager.isConnectionAvailable(
            syncMode = pref.value?.syncMode,
            cloudService = syncProvider.value?.type
        )

}
