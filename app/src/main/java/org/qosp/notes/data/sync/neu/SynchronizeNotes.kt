package org.qosp.notes.data.sync.neu

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.preferences.CloudService

class SynchronizeNotes(private val idMappingRepository: IdMappingRepository) {
    suspend operator fun invoke(
        localNotes: List<Note>, remoteNotes: List<RemoteNoteMetaData>, service: CloudService
    ): SyncNotesResult {
        // Fetch all mappings for this service at once to minimize idMapping queries (requirement #1)
        val allMappings = idMappingRepository.getAllByProvider(service)

        // Create maps for faster lookups
        val localNotesMap = localNotes.associateBy { it.id }
        val remoteNotesMap = remoteNotes.associateBy { it.id }

        // Maps for local note ID to remote note ID and vice versa
        val localToRemoteMap = allMappings.associateBy { it.localNoteId }
        val remoteToLocalMap = allMappings.associateBy {
            if (service == CloudService.NEXTCLOUD) it.remoteNoteId?.toString() ?: "" else it.storageUri ?: ""
        }.filterKeys { it != "" }


        val localUpdates = mutableListOf<NoteAction>()
        val remoteUpdates = mutableListOf<NoteAction>()

        // Process local notes
        for (localNote in localNotes) {
            val mapping = localToRemoteMap[localNote.id]

            if (mapping != null) {
                // Local note has a mapping to a remote note
                val remoteNote = when (service) {
                    CloudService.NEXTCLOUD -> mapping.remoteNoteId.toString()
                    CloudService.FILE_STORAGE -> mapping.storageUri
                    else -> null
                }?.let { remoteNotesMap[it] }

                if (remoteNote != null) {
                    // Both local and remote notes exist, compare last modified times
                    if (localNote.modifiedDate > remoteNote.lastModified) {
                        // Local note is newer, update remote
                        remoteUpdates.add(NoteAction.Update(localNote, remoteNote))
                    } else if (localNote.modifiedDate < remoteNote.lastModified) {
                        // Remote note is newer, update local
                        localUpdates.add(NoteAction.Update(localNote, remoteNote))
                    }
                    // If equal, no action needed
                } else {
                    // Remote note doesn't exist, create it with empty ID
                    // This happens when the remote note was deleted
                    val remoteNoteMetaData = RemoteNoteMetaData(
                        id = "", // Empty ID for new remote notes
                        title = localNote.title, lastModified = localNote.modifiedDate
                    )
                    remoteUpdates.add(NoteAction.Create(localNote, remoteNoteMetaData))
                }

            } else {
                // Local note has no mapping, create a new remote note
                val remoteNoteMetaData = RemoteNoteMetaData(
                    id = "", // Empty ID for new remote notes
                    title = localNote.title, lastModified = localNote.modifiedDate
                )
                remoteUpdates.add(NoteAction.Create(localNote, remoteNoteMetaData))
            }
        }

        // Process remote notes
        for (remoteNote in remoteNotes) {
            val mapping = remoteToLocalMap[remoteNote.id]
            if (mapping != null) {
                // Remote note has a mapping to a local note
                val localNote = localNotesMap[mapping.localNoteId]

                if (localNote == null || mapping.isDeletedLocally) {
                    // Local note doesn't exist anymore, delete the remote note
                    val dummyLocalNote = Note(id = mapping.localNoteId)
                    remoteUpdates.add(NoteAction.Delete(dummyLocalNote, remoteNote))
                }
                // If local note exists, it was already handled in the local notes loop
            } else {
                // Remote note has no mapping, create a new local note
                val newLocalNote = Note(
                    title = remoteNote.title, modifiedDate = remoteNote.lastModified
                )
                localUpdates.add(NoteAction.Create(newLocalNote, remoteNote))
            }
        }
        return SyncNotesResult(localUpdates, remoteUpdates)
    }
}

sealed interface NoteAction {
    data class Create(val note: Note, val remoteNoteMetaData: RemoteNoteMetaData) : NoteAction
    data class Update(val note: Note, val remoteNoteMetaData: RemoteNoteMetaData) : NoteAction
    data class Delete(val note: Note, val remoteNoteMetaData: RemoteNoteMetaData) : NoteAction
}

data class SyncNotesResult(
    val localUpdates: List<NoteAction>,
    val remoteUpdates: List<NoteAction>,
)
