package org.qosp.notes.data.sync.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.data.sync.nextcloud.NextcloudManager
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SyncMode
import org.qosp.notes.ui.utils.ConnectionManager

class SyncManager(
    private val preferenceRepository: PreferenceRepository,
    private val idMappingRepository: IdMappingRepository,
    val connectionManager: ConnectionManager,
    private val nextcloudManager: NextcloudManager,
    val syncingScope: CoroutineScope,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val prefs: Flow<SyncPrefs> = preferenceRepository.getAll().flatMapLatest { prefs ->
        when (prefs.cloudService) {
            CloudService.DISABLED -> flowOf(SyncPrefs(false, null, prefs.syncMode, null))
            CloudService.NEXTCLOUD -> {
                NextcloudConfig.fromPreferences(preferenceRepository).map { config ->
                    SyncPrefs(true, nextcloudManager, prefs.syncMode, config)
                }
            }
        }
    }

    val config = prefs.map { prefs -> prefs.config }
        .stateIn(syncingScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ObsoleteCoroutinesApi::class)
    private val actor = syncingScope.actor<Message> {

        for (msg in channel) {
            with(msg) {
                val result = when (this) {
                    is CreateNote -> provider.createNote(note, config)
                    is DeleteNote -> provider.deleteNote(note, config)
                    is MoveNoteToBin -> provider.moveNoteToBin(note, config)
                    is RestoreNote -> provider.restoreNote(note, config)
                    is Sync -> provider.sync(config)
                    is UpdateNote -> provider.updateNote(note, config)
                    is Authenticate -> provider.authenticate(config)
                    is IsServerCompatible -> provider.isServerCompatible(config)
                    is UpdateOrCreateNote -> {
                        val exists = idMappingRepository.getByLocalIdAndProvider(note.id, config.provider) != null
                        if (exists) provider.updateNote(note, config) else provider.createNote(note, config)
                    }
                }

                deferred.complete(result)
            }
        }
    }

    suspend inline fun ifSyncing(
        customConfig: ProviderConfig? = null,
        fallback: () -> Unit = {},
        block: (SyncProvider, ProviderConfig) -> BaseResult,
    ): BaseResult {
        val (isEnabled, provider, mode, prefConfig) = prefs.first()
        val config = customConfig ?: prefConfig

        return when {
            !isEnabled -> SyncingNotEnabled.also { fallback() }
            provider == null || config == null -> InvalidConfig.also { fallback() }
            !connectionManager.isConnectionAvailable(mode) -> NoConnectivity.also { fallback() }
            else -> block(provider, config)
        }
    }

    private suspend inline fun sendMessage(
        customConfig: ProviderConfig? = null,
        crossinline block: suspend (SyncProvider, ProviderConfig) -> Message,
    ): BaseResult {
        return ifSyncing(customConfig) { provider, config ->
            val message = block(provider, config)
            actor.send(message)
            message.deferred.await()
        }
    }

    suspend fun sync() = sendMessage { provider, config -> Sync(provider, config) }

    suspend fun createNote(note: Note) = sendMessage { provider, config -> CreateNote(note, provider, config) }

    suspend fun deleteNote(note: Note) = sendMessage { provider, config -> DeleteNote(note, provider, config) }

    suspend fun moveNoteToBin(note: Note) = sendMessage { provider, config -> MoveNoteToBin(note, provider, config) }

    suspend fun restoreNote(note: Note) = sendMessage { provider, config -> RestoreNote(note, provider, config) }

    suspend fun updateNote(note: Note) = sendMessage { provider, config -> UpdateNote(note, provider, config) }

    suspend fun updateOrCreate(note: Note) = sendMessage { provider, config -> UpdateOrCreateNote(note, provider, config) }

    suspend fun isServerCompatible(customConfig: ProviderConfig? = null) = sendMessage(customConfig) { provider, config ->
        IsServerCompatible(provider, config)
    }

    suspend fun authenticate(customConfig: ProviderConfig? = null) = sendMessage(customConfig) { provider, config ->
        Authenticate(provider, config)
    }
}

data class SyncPrefs(
    val isEnabled: Boolean,
    val provider: SyncProvider?,
    val mode: SyncMode,
    val config: ProviderConfig?,
)

private sealed class Message(val provider: SyncProvider, val config: ProviderConfig) {
    val deferred: CompletableDeferred<BaseResult> = CompletableDeferred()
}
private class CreateNote(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class UpdateNote(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class UpdateOrCreateNote(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class DeleteNote(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class RestoreNote(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class MoveNoteToBin(val note: Note, provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class Sync(provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class Authenticate(provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
private class IsServerCompatible(provider: SyncProvider, config: ProviderConfig) : Message(provider, config)
