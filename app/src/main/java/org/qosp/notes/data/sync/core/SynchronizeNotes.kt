package org.qosp.notes.data.sync.core

import android.util.Log
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.preferences.CloudService
import kotlin.math.abs

class SynchronizeNotes(private val idMappingRepository: IdMappingRepository) {
    private val tag = SynchronizeNotes::class.java.simpleName

    suspend operator fun invoke(
        localNotes: List<Note>,
        remoteNotes: List<RemoteNoteMetaData>,
        service: CloudService,
        methode: SyncMethod = SyncMethod.MAPPING
    ): SyncNotesResult {
        Log.d(tag, "SynchronizeNotes: Starting synchronization")
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
                    if (abs(localNote.modifiedDate - remoteNote.lastModified) <= 1) {
                        // no action
                    } else if (localNote.modifiedDate > remoteNote.lastModified) {
                        // Local note is newer, update remote
                        Log.d(
                            tag,
                            "Local note (${localNote.title}) is newer ${localNote.modifiedDate}, ${remoteNote.lastModified}"
                        )
                        remoteUpdates.add(NoteAction.Update(localNote, remoteNote))
                    } else if (localNote.modifiedDate < remoteNote.lastModified) {
                        // Remote note is newer, update local
                        Log.d(
                            tag,
                            "Remote note(${remoteNote.title}) is newer ${remoteNote.lastModified}, ${localNote.modifiedDate}"
                        )
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
                    Log.d(tag, "May be deleted remotely: $remoteNoteMetaData")
                    localUpdates.add(NoteAction.Delete(localNote, remoteNoteMetaData))
                }

            } else {
                // Local note has no mapping, create a new remote note
                val remoteNoteMetaData = RemoteNoteMetaData(
                    id = "", // Empty ID for new remote notes
                    title = localNote.title, lastModified = localNote.modifiedDate
                )
                Log.d(tag, "New local note: ${localNote.title}")
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
                    Log.d(tag, "Local note deleted. Deleting remotely: ${remoteNote.title}")
                    remoteUpdates.add(NoteAction.Delete(dummyLocalNote, remoteNote))
                }
                // If the local note exists, it was already handled in the local notes loop
            } else {
                // Remote note has no mapping, create a new local note
                val newLocalNote = Note(
                    title = remoteNote.title, modifiedDate = remoteNote.lastModified
                )
                Log.d(tag, "New Remote note: ${remoteNote.title}")
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
