package org.qosp.notes.data.sync.core

import android.net.Uri
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository

class BackendManager<C : ProviderConfig, N : SyncNote>(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val idMappingRepository: IdMappingRepository,
    private val backend: ISyncBackend<C, N>,
    private val config: C
) : ISyncProvider {


    override fun getConfig() = (config as? ProviderConfig)

    override val service = backend.service

    override suspend fun sync(): BaseResult {
        val localFiles = noteRepository.getAll().first().filterNot { it.isLocalOnly }.associateBy { it.id }
        val remoteFiles = when (val r = backend.list(config)) {
            is SyncResult.Success -> r.data
            else -> return GenericError("error listing files")
        }
        val mappingsByLocal = idMappingRepository.getAllByProvider(service).associateBy { it.localNoteId }
        val actionsForLocal = mutableListOf<Action<N>>()
        val actionsForRemote = mutableListOf<Action<N>>()

        val processedLocalFiles = mutableListOf<Note>()
        val processedRemoteFiles = mutableListOf<SyncNote>()

        for (localFile in localFiles.values) {
            val remoteFile = remoteFiles.find {
                when (it) {
                    is NextcloudNote -> mappingsByLocal[localFile.id]?.remoteNoteId == it.id
                    is NoteFile -> mappingsByLocal[localFile.id]?.storageUri == it.uri?.toString()
                    else -> false
                }
            }
            when {
                remoteFile == null -> actionsForRemote.add(Action.Add(localFile.title, localFile.content))

                localFile.modifiedDate > remoteFile.modified -> actionsForRemote.add(
                    Action.UpdateRemote(localFile.title, localFile.content, remoteFile)
                )

                localFile.isDeleted -> actionsForRemote.add(Action.DeleteRemote(remoteFile))
            }
        }

        val mappingByRemoteId = mappingsByLocal.values.associateBy { it.remoteNoteId }
        val mappingByStorageUri = mappingsByLocal.values.associateBy { it.storageUri }
        for (remoteFile in remoteFiles) {
            val localFileId = when (remoteFile) {
                is NextcloudNote -> mappingByRemoteId[remoteFile.id]?.localNoteId
                is NoteFile -> mappingByStorageUri[remoteFile.uri?.toString()]?.localNoteId
                else -> null
            }
            val localFile = localFileId?.let { localFiles[it] }
            when {
                localFile == null -> actionsForLocal.add(Action.AddLocal(remoteFile))
                remoteFile.modified > localFile.modifiedDate -> actionsForLocal.add(
                    Action.UpdateLocal(localFile.id, remoteFile)
                )
            }
        }
        // Deleted remote files
        val deletedRemotes = if (remoteFiles.firstOrNull() is NextcloudNote) {
            (mappingByRemoteId - remoteFiles.map { it as? NextcloudNote }.mapNotNull { it?.id }.toSet()).values
        } else if (remoteFiles.firstOrNull() is NoteFile) {
            (mappingByStorageUri - remoteFiles.map { it as? NoteFile }.mapNotNull { it?.uri?.toString() }
                .toSet()).values
        } else {
            emptySet()
        }

//        performActions(localRepo, actionsForLocal)
//        performActions(remoteRepo, actionsForRemote)
        return Success

    }

//    private suspend fun performActions(repo: Repository<File>, actions: List<Action>) {
//        for (action in actions) {
//            when (action) {
//                is Action.Add -> repo.add(action.name, action.content)
//                is Action.Update -> repo.update(action.name, action.content)
//                is Action.DeleteLocal -> repo.delete(action.name)
//            }
//        }
//    }

    override suspend fun createNote(note: Note) = transformSyncResult(backend.createNote(note, config)) {
        val idMapping = it.getIdMappingFor(note)
        idMappingRepository.assignProviderToNote(idMapping)
        Success
    }

    override suspend fun deleteNote(note: Note): BaseResult {
        val idMapping =
            idMappingRepository.getByLocalIdAndProvider(note.id, service) ?: return GenericError("no id mapping found")
        val syncNote = NoteFile(0, content = null, title = "", uri = Uri.parse(idMapping.storageUri))
        return transformSyncResult(
            backend.deleteNote(syncNote as N, config) // TODO this casting is not great.
        ) {
            idMappingRepository.delete(idMapping)
            Success
        }
    }

    override suspend fun updateNote(note: Note): BaseResult {
        val idMapping =
            idMappingRepository.getByLocalIdAndProvider(note.id, service) ?: return GenericError("no id mapping found")
        val syncNote = backend.getSyncNoteFrom(note, idMapping)

        return transformSyncResult(backend.updateNote(syncNote, config)) {
            Success
        }
    }

    override suspend fun authenticate(config: ProviderConfig?): BaseResult =
        if (config == null) backend.authenticate(this.config)
        else (config as? C)?.let { backend.authenticate(it) } ?: InvalidConfig

    override suspend fun isServerCompatible(config: ProviderConfig?): BaseResult =
        if (config == null) backend.isServerCompatible(this.config)
        else (config as? C)?.let { backend.isServerCompatible(it) } ?: InvalidConfig

    private suspend fun <T> transformSyncResult(
        result: SyncResult<T>,
        transform: suspend (T) -> BaseResult
    ): BaseResult =
        when (result) {
            is SyncResult.Success -> transform(result.data)
            is SyncResult.Error -> GenericError(result.error.message ?: "sync error")
        }

}

sealed class Action<N : SyncNote> {
    data class Add<N : SyncNote>(val name: String, val content: String, val note: N? = null) : Action<N>()
    data class AddLocal<N : SyncNote>(val note: N) : Action<N>()
    data class UpdateLocal<N : SyncNote>(val id: Long, val note: N) : Action<N>()
    data class UpdateRemote<N : SyncNote>(val name: String, val content: String, val note: N) : Action<N>()
    data class DeleteLocal<N : SyncNote>(val id: Long) : Action<N>()
    data class DeleteRemote<N : SyncNote>(val note: N) : Action<N>()
}
