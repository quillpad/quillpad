package org.qosp.notes.data.sync.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.fs.StorageBackend
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.data.sync.nextcloud.NextcloudBackend
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.CloudService.DISABLED
import org.qosp.notes.preferences.CloudService.FILE_STORAGE
import org.qosp.notes.preferences.CloudService.NEXTCLOUD
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.utils.ConnectionManager
import org.qosp.notes.ui.utils.collect
import javax.inject.Provider

class SyncManager(
    private val preferenceRepository: PreferenceRepository,
    private val idMappingRepository: IdMappingRepository,
    private val connectionManager: ConnectionManager,
    private val notebookRepositoryProvider: Provider<NotebookRepository>,
    private val noteRepositoryProvider: Provider<NoteRepository>,
    private val context: Context,
    private val nextcloudBackend: NextcloudBackend,
    private val storageBackend: StorageBackend,
    val syncingScope: CoroutineScope,
) {

    private val syncService: Flow<CloudService> = preferenceRepository.getAll().map { it.cloudService }
    private val pref: StateFlow<AppPreferences?> =
        preferenceRepository.getAll().stateIn(syncingScope, SharingStarted.Eagerly, null)

    val syncProvider: StateFlow<ISyncProvider?> = combine(
        syncService,
        NextcloudConfig.fromPreferences(preferenceRepository),
        StorageConfig.storageLocation(preferenceRepository, context)
    ) { service, nextcloudConfig, storageConfig ->
        when (service) {
            DISABLED -> null
            NEXTCLOUD -> nextcloudConfig?.let {
                BackendManager(
                    noteRepositoryProvider.get(),
                    notebookRepositoryProvider.get(),
                    idMappingRepository,
                    nextcloudBackend,
                    it
                )
            }

            FILE_STORAGE -> storageConfig?.let {
                BackendManager(
                    noteRepositoryProvider.get(),
                    notebookRepositoryProvider.get(),
                    idMappingRepository,
                    storageBackend,
                    it
                )
            }
        }
    }.stateIn(syncingScope, SharingStarted.Eagerly, null)

    val config = syncProvider.map { it?.getConfig() }

    private val tasksFlow: MutableSharedFlow<Pair<Message, ProviderConfig?>> = MutableSharedFlow()

    init {
        tasksFlow.collect(syncingScope) { (msg, config) ->
            val sync = syncProvider.value
            if (sync == null) {
                msg.deferred.complete(SyncingNotEnabled)
            } else if (!connectionManager.isConnectionAvailable(pref.value?.syncMode)) {
                msg.deferred.complete(NoConnectivity)
            } else {
                Log.i(TAG, "Doing message: $msg")
                val result = when (msg) {
                    is CreateNote -> sync.createNote(msg.note)
                    is DeleteNote -> sync.deleteNote(msg.note)
                    is Sync -> sync.sync()
                    is UpdateNote -> sync.updateNote(msg.note)
                    is Authenticate -> sync.authenticate(config)
                    is IsServerCompatible -> sync.isServerCompatible(config)
                    is UpdateOrCreateNote -> {
                        val exists = idMappingRepository.getByLocalIdAndProvider(msg.note.id, sync.service) != null
                        if (exists) sync.updateNote(msg.note) else sync.createNote(msg.note)
                    }
                }
                msg.deferred.complete(result)
            }
        }
    }

    val isSyncing: Boolean
        get() = syncProvider.value != null && connectionManager.isConnectionAvailable(pref.value?.syncMode)

    private suspend inline fun sendMessage(
        customConfig: ProviderConfig? = null,
        crossinline block: suspend () -> Message,
    ): BaseResult {
        val message = block()
        tasksFlow.emit(message to customConfig)
        val result = message.deferred.await()
        Log.i(TAG, "sendMessage: Got result $result")
        return result
    }

    suspend fun sync() = sendMessage { Sync() }

    suspend fun createNote(note: Note) = sendMessage { CreateNote(note) }

    suspend fun deleteNote(note: Note) = sendMessage { DeleteNote(note) }

    suspend fun updateNote(note: Note) = sendMessage { UpdateNote(note) }

    suspend fun updateOrCreate(note: Note) = sendMessage { UpdateOrCreateNote(note) }

    suspend fun isServerCompatible(customConfig: ProviderConfig? = null) =
        sendMessage(customConfig) { IsServerCompatible() }

    suspend fun authenticate(customConfig: ProviderConfig? = null) = sendMessage(customConfig) { Authenticate() }

    companion object {
        private const val TAG = "SyncManager"
    }
}

private sealed class Message {
    val deferred: CompletableDeferred<BaseResult> = CompletableDeferred()
    override fun toString(): String = this::class.java.simpleName
}

private class CreateNote(val note: Note) : Message()
private class UpdateNote(val note: Note) : Message()
private class UpdateOrCreateNote(val note: Note) : Message()
private class DeleteNote(val note: Note) : Message()
private class Sync : Message()
private class Authenticate : Message()
private class IsServerCompatible : Message()
