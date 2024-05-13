package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.InvalidConfig
import org.qosp.notes.data.sync.core.NextcloudNote
import org.qosp.notes.data.sync.core.ServerNotSupported
import org.qosp.notes.data.sync.core.ServerNotSupportedException
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.core.SyncResult
import org.qosp.notes.data.sync.nextcloud.model.asNewLocalNote
import org.qosp.notes.data.sync.nextcloud.model.asNextcloudNote
import org.qosp.notes.preferences.CloudService

class NextcloudBackend(
    private val nextcloudAPI: NextcloudAPI,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val idMappingRepository: IdMappingRepository,
) : ISyncBackend<NextcloudConfig, NextcloudNote> {

    override suspend fun createNote(note: Note, config: NextcloudConfig): SyncResult<NextcloudNote> {
        val nextcloudNote = note.asNextcloudNote()
        if (nextcloudNote.id != 0L) return SyncResult.Error(Exception("Note already exists."))
        return tryCalling { nextcloudAPI.createNote(nextcloudNote, config) }
    }

    override suspend fun getNoteContent(note: NextcloudNote, config: NextcloudConfig) =
        tryCalling { nextcloudAPI.getNote(note.id, config) }

    override suspend fun getSyncNoteFrom(note: Note, idMapping: IdMapping): NextcloudNote {
        return NextcloudNote(
            id = idMapping.remoteNoteId ?: 0L,
            content = note.content,
            title = note.title,
            category = note.notebookId?.let { notebookRepository.getById(it).first()?.name } ?: "",
            favorite = note.isPinned,
            modified = note.modifiedDate,
            readOnly = null,
            etag = idMapping.extras,
        )
    }

    override suspend fun list(config: NextcloudConfig) = tryCalling {
        nextcloudAPI.getNotes(config).map { it as SyncNote } // Dirty code
    }

    override suspend fun deleteNote(note: NextcloudNote, config: NextcloudConfig): SyncResult<Unit> {
        return tryCalling { nextcloudAPI.deleteNote(note, config) }
    }

    override suspend fun updateNote(note: NextcloudNote, config: NextcloudConfig): SyncResult<Unit> {
        return tryCalling {
            updateNoteWithEtag(note, config)
        }
    }

    override suspend fun authenticate(config: NextcloudConfig): BaseResult {
        val result = kotlin.runCatching {
            nextcloudAPI.testCredentials(config)
        }
        return if (result.isSuccess) Success else InvalidConfig
    }

    override suspend fun isServerCompatible(config: NextcloudConfig): BaseResult {
        val result = kotlin.runCatching {
            val capabilities = nextcloudAPI.getNotesCapabilities(config)!!
            val maxServerVersion = capabilities.apiVersion.last().toFloat()

            if (MIN_SUPPORTED_VERSION.toFloat() > maxServerVersion) throw ServerNotSupportedException
        }
        return if (result.isSuccess) Success else ServerNotSupported
    }

//    suspend fun sync(config: NextcloudConfig): SyncResult<Unit> {
//        suspend fun handleConflict(local: Note, remote: NextcloudNote, mapping: IdMapping) {
//            if (mapping.isDeletedLocally) return
//
//            if (remote.modified < local.modifiedDate) {
//                // Remote version is outdated
//                updateNoteWithEtag(local, remote, mapping.extras, config)
//
//                // Nextcloud does not update change the modification date when a note is starred
//            } else if (remote.modified > local.modifiedDate || remote.favorite != local.isPinned) {
//                // Local version is outdated
//                noteRepository.updateNotes(remote.asUpdatedLocalNote(local))
//                idMappingRepository.update(
//                    mapping.copy(
//                        extras = remote.etag,
//                    )
//                )
//            }
//        }
//
//        return tryCalling {
//            // Fetch notes from the cloud
//            val nextcloudNotes = nextcloudAPI.getNotes(config)
//
//            val localNoteIds = noteRepository
//                .getAll()
//                .first()
//                .map { it.id }
//
//            val localNotes = noteRepository
//                .getNonDeleted()
//                .first()
//                .filterNot { it.isLocalOnly }
//
//            val idsInUse = mutableListOf<Long>()
//
//            // Remove id mappings for notes that do not exist
//            idMappingRepository.deleteIfLocalIdNotIn(localNoteIds)
//
//            // Handle conflicting notes
//            for (remoteNote in nextcloudNotes) {
//                idsInUse.add(remoteNote.id)
//
//                when (val mapping = idMappingRepository.getByRemoteId(remoteNote.id, CloudService.NEXTCLOUD)) {
//                    null -> {
//                        // New note, we have to create it locally
//                        val localNote = remoteNote.asNewLocalNote()
//                        val localId = noteRepository.insertNote(localNote, shouldSync = false)
//                        idMappingRepository.insert(
//                            IdMapping(
//                                localNoteId = localId,
//                                remoteNoteId = remoteNote.id,
//                                provider = CloudService.NEXTCLOUD,
//                                isDeletedLocally = false,
//                                extras = remoteNote.etag
//                            )
//                        )
//                    }
//
//                    else -> {
//                        if (mapping.isDeletedLocally && mapping.remoteNoteId != null) {
//                            nextcloudAPI.deleteNote(remoteNote, config)
//                            continue
//                        }
//
//                        if (mapping.isBeingUpdated) continue
//
//                        val localNote = localNotes.find { it.id == mapping.localNoteId }
//                        if (localNote != null) handleConflict(
//                            local = localNote,
//                            remote = remoteNote,
//                            mapping = mapping,
//                        )
//                    }
//                }
//            }
//
//            // Delete notes that have been deleted remotely
//            noteRepository.moveRemotelyDeletedNotesToBin(idsInUse, CloudService.NEXTCLOUD)
//            idMappingRepository.unassignProviderFromRemotelyDeletedNotes(idsInUse, CloudService.NEXTCLOUD)
//
//            // Finally, upload any new local notes that are not mapped to any remote id
//            val newLocalNotes = noteRepository.getNonRemoteNotes(CloudService.NEXTCLOUD).first()
//            newLocalNotes.forEach {
//                val newRemoteNote = nextcloudAPI.createNote(it.asNextcloudNote(), config)
//                idMappingRepository.assignProviderToNote(
//                    IdMapping(
//                        localNoteId = it.id,
//                        remoteNoteId = newRemoteNote.id,
//                        provider = CloudService.NEXTCLOUD,
//                        isDeletedLocally = false,
//                        extras = newRemoteNote.etag,
//                    )
//                )
//            }
//        }
//    }

    private suspend fun updateNoteWithEtag(nextcloudNote: NextcloudNote, config: NextcloudConfig) {
        nextcloudAPI.updateNote(nextcloudNote, nextcloudNote.etag ?: "", config)
    }

    private suspend fun Note.asNextcloudNote(newId: Long? = null): NextcloudNote {
        val id = newId ?: idMappingRepository.getByLocalIdAndProvider(id, CloudService.NEXTCLOUD)?.remoteNoteId
        val notebookName = notebookId?.let { notebookRepository.getById(it).first()?.name }
        return asNextcloudNote(id = id ?: 0L, category = notebookName ?: "")
    }

//    private suspend fun NextcloudNote.asUpdatedLocalNote(note: Note) = note.copy(
//        title = title,
//        taskList = if (note.isList) note.mdToTaskList(syncContent) else listOf(),
//        content = syncContent,
//        isPinned = favorite,
//        modifiedDate = modified,
//        notebookId = getNotebookIdForCategory(category)
//    )

    private suspend fun NextcloudNote.asNewLocalNote(newId: Long? = null): Note {
        val id = newId ?: idMappingRepository.getByRemoteId(id, CloudService.NEXTCLOUD)?.localNoteId
        val notebookId = getNotebookIdForCategory(category)
        return asNewLocalNote(id = id ?: 0L, notebookId = notebookId)
    }

    private suspend fun getNotebookIdForCategory(category: String): Long? {
        return category
            .takeUnless { it.isBlank() }
            ?.let {
                notebookRepository.getByName(it).first()?.id ?: notebookRepository.insert(Notebook(name = category))
            }
    }

    private inline fun <T> tryCalling(block: () -> T): SyncResult<T> {
        return try {
            SyncResult.Success(block())
        } catch (e: Exception) {
            Log.e(Tag, e.message.toString())
            SyncResult.Error(e)
        }
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = 1
        const val Tag = "NextcloudManager"
    }

    override val service: CloudService
        get() = CloudService.NEXTCLOUD

}
