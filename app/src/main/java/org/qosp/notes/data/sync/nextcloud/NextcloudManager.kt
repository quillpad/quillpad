package org.qosp.notes.data.sync.nextcloud

import kotlinx.coroutines.flow.first
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.ApiError
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.GenericError
import org.qosp.notes.data.sync.core.InvalidConfig
import org.qosp.notes.data.sync.core.ProviderConfig
import org.qosp.notes.data.sync.core.ServerNotSupported
import org.qosp.notes.data.sync.core.ServerNotSupportedException
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncProvider
import org.qosp.notes.data.sync.core.Unauthorized
import org.qosp.notes.data.sync.nextcloud.model.NextcloudNote
import org.qosp.notes.data.sync.nextcloud.model.asNewLocalNote
import org.qosp.notes.data.sync.nextcloud.model.asNextcloudNote
import org.qosp.notes.preferences.CloudService
import retrofit2.HttpException

class NextcloudManager(
    private val nextcloudAPI: NextcloudAPI,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val idMappingRepository: IdMappingRepository,
) : SyncProvider {

    override suspend fun createNote(
        note: Note,
        config: ProviderConfig
    ): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        val nextcloudNote = note.asNextcloudNote()

        if (nextcloudNote.id != 0L) return GenericError("Cannot create note that already exists")

        return tryCalling {
            val savedNote = nextcloudAPI.createNote(nextcloudNote, config)
            idMappingRepository.assignProviderToNote(
                IdMapping(
                    localNoteId = note.id,
                    remoteNoteId = savedNote.id,
                    provider = CloudService.NEXTCLOUD,
                    extras = savedNote.etag,
                    isDeletedLocally = false,
                ),
            )
        }
    }

    override suspend fun deleteNote(
        note: Note,
        config: ProviderConfig
    ): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        val nextcloudNote = note.asNextcloudNote()

        if (nextcloudNote.id == 0L) return GenericError("Cannot delete note that does not exist.")

        return tryCalling {
            nextcloudAPI.deleteNote(nextcloudNote, config)
            idMappingRepository.deleteByRemoteId(CloudService.NEXTCLOUD, nextcloudNote.id)
        }
    }

    override suspend fun moveNoteToBin(note: Note, config: ProviderConfig): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        val nextcloudNote = note.asNextcloudNote()

        if (nextcloudNote.id == 0L) return GenericError("Cannot delete note that does not exist.")

        return tryCalling {
            nextcloudAPI.deleteNote(nextcloudNote, config)
            idMappingRepository.unassignProviderFromNote(CloudService.NEXTCLOUD, note.id)
        }
    }

    override suspend fun restoreNote(note: Note, config: ProviderConfig) = createNote(note, config)

    override suspend fun updateNote(
        note: Note,
        config: ProviderConfig
    ): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        val nextcloudNote = note.asNextcloudNote()

        if (nextcloudNote.id == 0L) return GenericError("Cannot update note that does not exist.")

        return tryCalling {
            updateNoteWithEtag(note, nextcloudNote, null, config)
        }
    }

    override suspend fun authenticate(config: ProviderConfig): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        return tryCalling {
            nextcloudAPI.testCredentials(config)
        }
    }

    override suspend fun isServerCompatible(config: ProviderConfig): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        return tryCalling {
            val capabilities = nextcloudAPI.getNotesCapabilities(config)!!
            val maxServerVersion = capabilities.apiVersion.last().toFloat()

            if (MIN_SUPPORTED_VERSION.toFloat() > maxServerVersion) throw ServerNotSupportedException
        }
    }

    override suspend fun sync(config: ProviderConfig): BaseResult {
        if (config !is NextcloudConfig) return InvalidConfig

        suspend fun handleConflict(local: Note, remote: NextcloudNote, mapping: IdMapping) {
            if (mapping.isDeletedLocally) return

            if (remote.modified < local.modifiedDate) {
                // Remote version is outdated
                updateNoteWithEtag(local, remote, mapping.extras, config)

                // Nextcloud does not update change the modification date when a note is starred
            } else if (remote.modified > local.modifiedDate || remote.favorite != local.isPinned) {
                // Local version is outdated
                noteRepository.updateNotes(remote.asUpdatedLocalNote(local))
                idMappingRepository.update(
                    mapping.copy(
                        extras = remote.etag,
                    )
                )
            }
        }

        return tryCalling {
            // Fetch notes from the cloud
            val nextcloudNotes = nextcloudAPI.getNotes(config)

            val localNoteIds = noteRepository
                .getAll()
                .first()
                .map { it.id }

            val localNotes = noteRepository
                .getNonDeleted()
                .first()
                .filterNot { it.isLocalOnly }

            val idsInUse = mutableListOf<Long>()

            // Remove id mappings for notes that do not exist
            idMappingRepository.deleteIfLocalIdNotIn(localNoteIds)

            // Handle conflicting notes
            for (remoteNote in nextcloudNotes) {
                idsInUse.add(remoteNote.id)

                when (val mapping = idMappingRepository.getByRemoteId(remoteNote.id, CloudService.NEXTCLOUD)) {
                    null -> {
                        // New note, we have to create it locally
                        val localNote = remoteNote.asNewLocalNote()
                        val localId = noteRepository.insertNote(localNote, shouldSync = false)
                        idMappingRepository.insert(
                            IdMapping(
                                localNoteId = localId,
                                remoteNoteId = remoteNote.id,
                                provider = CloudService.NEXTCLOUD,
                                isDeletedLocally = false,
                                extras = remoteNote.etag
                            )
                        )
                    }
                    else -> {
                        if (mapping.isDeletedLocally && mapping.remoteNoteId != null) {
                            nextcloudAPI.deleteNote(remoteNote, config)
                            continue
                        }

                        if (mapping.isBeingUpdated) continue

                        val localNote = localNotes.find { it.id == mapping.localNoteId }
                        if (localNote != null) handleConflict(
                            local = localNote,
                            remote = remoteNote,
                            mapping = mapping,
                        )
                    }
                }
            }

            // Delete notes that have been deleted remotely
            noteRepository.moveRemotelyDeletedNotesToBin(idsInUse, CloudService.NEXTCLOUD)
            idMappingRepository.unassignProviderFromRemotelyDeletedNotes(idsInUse, CloudService.NEXTCLOUD)

            // Finally, upload any new local notes that are not mapped to any remote id
            val newLocalNotes = noteRepository.getNonRemoteNotes(CloudService.NEXTCLOUD).first()
            newLocalNotes.forEach {
                val newRemoteNote = nextcloudAPI.createNote(it.asNextcloudNote(), config)
                idMappingRepository.assignProviderToNote(
                    IdMapping(
                        localNoteId = it.id,
                        remoteNoteId = newRemoteNote.id,
                        provider = CloudService.NEXTCLOUD,
                        isDeletedLocally = false,
                        extras = newRemoteNote.etag,
                    )
                )
            }
        }
    }

    private suspend fun updateNoteWithEtag(
        note: Note,
        nextcloudNote: NextcloudNote,
        etag: String? = null,
        config: NextcloudConfig
    ) {
        val cloudId = idMappingRepository.getByRemoteId(nextcloudNote.id, CloudService.NEXTCLOUD) ?: return
        val etag = etag ?: cloudId.extras
        val newNote = nextcloudAPI.updateNote(
            note.asNextcloudNote(nextcloudNote.id),
            etag.toString(),
            config,
        )

        idMappingRepository.update(
            cloudId.copy(extras = newNote.etag, isBeingUpdated = false)
        )
    }

    private suspend fun Note.asNextcloudNote(newId: Long? = null): NextcloudNote {
        val id = newId ?: idMappingRepository.getByLocalIdAndProvider(id, CloudService.NEXTCLOUD)?.remoteNoteId
        val notebookName = notebookId?.let { notebookRepository.getById(it).first()?.name }
        return asNextcloudNote(id = id ?: 0L, category = notebookName ?: "")
    }

    private suspend fun NextcloudNote.asUpdatedLocalNote(note: Note) = note.copy(
        title = title,
        content = content,
        isPinned = favorite,
        modifiedDate = modified,
        notebookId = getNotebookIdForCategory(category)
    )

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

    private inline fun tryCalling(block: () -> Unit): BaseResult {
        return try {
            block()
            Success
        } catch (e: Exception) {
            when (e) {
                ServerNotSupportedException -> ServerNotSupported
                is HttpException -> {
                    when (e.code()) {
                        401 -> Unauthorized
                        else -> ApiError(e.message(), e.code())
                    }
                }
                else -> GenericError(e.message.toString())
            }
        }
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = 1
    }
}
