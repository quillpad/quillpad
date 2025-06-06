package org.qosp.notes.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.sync.neu.BackendProvider
import org.qosp.notes.data.sync.neu.SynchronizeNotes
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.utils.ConnectionManager

const val SYNC_SCOPE = "Sync"

@Module
object SyncModule {

    @Single
    @Named(SYNC_SCOPE)
    fun provideSyncScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Single
    fun backendProvider(
        context: Context,
        connectionManager: ConnectionManager,
        nextcloudApi: NextcloudAPI,
        preferenceRepository: PreferenceRepository,
        @Named(SYNC_SCOPE) syncingScope: CoroutineScope
    ) = BackendProvider(
        context = context,
        nextcloudApi = nextcloudApi,
        preferenceRepository = preferenceRepository,
        syncingScope = syncingScope,
        connectionManager = connectionManager,
    )

    @Single
    fun synchronizeNotes(idMappingRepository: IdMappingRepository) =
        SynchronizeNotes(idMappingRepository = idMappingRepository)
}
