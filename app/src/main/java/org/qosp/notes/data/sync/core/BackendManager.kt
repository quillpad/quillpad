package org.qosp.notes.data.sync.core

import android.net.Uri
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

    override suspend fun sync(): BaseResult {
        TODO()
        return Success
    }

    override fun getConfig() = (config as? ProviderConfig)

    override val service = backend.service

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
