package org.qosp.notes.data.sync.core

import android.util.Log
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.sync.getMapping
import org.qosp.notes.preferences.CloudService
import kotlin.math.abs

class SynchronizeNotes(private val idMappingRepository: IdMappingRepository) {
    private val tag = SynchronizeNotes::class.java.simpleName

    private fun logDebug(message: String) = Log.d(tag, message)

    suspend operator fun invoke(
        localNotes: List<Note>,
        remoteNotes: List<SyncNote>,
        service: CloudService,
        method: SyncMethod = SyncMethod.MAPPING
    ): SyncNotesResult {
        logDebug("SynchronizeNotes: Starting synchronization with method: $method")

        val localUpdates = mutableListOf<NoteAction>()
        val remoteUpdates = mutableListOf<NoteAction>()
        val mappingUpdates = mutableListOf<IdMapping>()

        when (method) {
            SyncMethod.MAPPING -> {
                // Fetch all mappings for this service at once to minimize idMapping queries (requirement #1)
                val allMappings = idMappingRepository.getAllByProvider(service)

                // Create maps for faster lookups
                val localNotesMap = localNotes.associateBy { it.id }
                val remoteNotesMap = remoteNotes.associateBy { it.idStr }

                // Maps for local note ID to remote note ID and vice versa
                val localToRemoteMap = allMappings.associateBy { it.localNoteId }
                val remoteToLocalMap = allMappings.associateBy {
                    if (service == CloudService.NEXTCLOUD) it.remoteNoteId?.toString() ?: "" else it.storageUri ?: ""
                }.filterKeys { it != "" }

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
                                logDebug("Local note (${localNote.title}) is newer ${localNote.modifiedDate}, ${remoteNote.lastModified}")
                                remoteUpdates.add(NoteAction.Update(localNote, remoteNote))
                            } else if (localNote.modifiedDate < remoteNote.lastModified) {
                                // Remote note is newer, update local
                                logDebug("Remote note(${remoteNote.title}) is newer ${remoteNote.lastModified}, ${localNote.modifiedDate}")
                                localUpdates.add(NoteAction.Update(localNote, remoteNote))
                            }
                            // If equal, no action needed
                        } else {
                            // Remote note doesn't exist
                            // This happens when the remote note was deleted
                            val syncNote = SyncNote(
                                title = localNote.title,
                                lastModified = localNote.modifiedDate,
                                content = localNote.content,
                                idStr = "", // Empty ID for new remote notes
                                id = 0,
                            )
                            logDebug("May be deleted remotely: $syncNote")
                            localUpdates.add(NoteAction.Delete(localNote, syncNote))
                        }

                    } else {
                        // Local note has no mapping, create a new remote note
                        val remoteNoteMetaData = SyncNote(
                            id = 0, // Empty ID for new remote notes
                            idStr = "", content = localNote.content,
                            title = localNote.title, lastModified = localNote.modifiedDate
                        )
                        logDebug("New local note: ${localNote.title}")
                        remoteUpdates.add(NoteAction.Create(localNote, remoteNoteMetaData))
                    }
                }

                // Process remote notes
                for (remoteNote in remoteNotes) {
                    val mapping = remoteToLocalMap[remoteNote.idStr]
                    if (mapping != null) {
                        // Remote note has a mapping to a local note
                        val localNote = localNotesMap[mapping.localNoteId]

                        if (localNote == null || mapping.isDeletedLocally) {
                            // Local note doesn't exist anymore, delete the remote note
                            val dummyLocalNote = Note(id = mapping.localNoteId)
                            logDebug("Local note deleted. Deleting remotely: ${remoteNote.title}")
                            remoteUpdates.add(NoteAction.Delete(dummyLocalNote, remoteNote))
                        }
                        // If the local note exists, it was already handled in the local notes loop
                    } else {
                        // Remote note has no mapping, create a new local note
                        val newLocalNote = Note(
                            title = remoteNote.title, modifiedDate = remoteNote.lastModified
                        )
                        logDebug("New Remote note: ${remoteNote.title}")
                        localUpdates.add(NoteAction.Create(newLocalNote, remoteNote))
                    }
                }
            }

            SyncMethod.TITLE -> {
                // Create maps for faster lookups based on title
                val localNotesMap = localNotes.associateBy { it.title }
                val remoteNotesMap = remoteNotes.associateBy { it.title }

                // Process local notes
                for (localNote in localNotes) {
                    val remoteNote = remoteNotesMap[localNote.title]

                    if (remoteNote != null) {
                        // Both local and remote notes exist with the same title, compare last modified times
                        if (abs(localNote.modifiedDate - remoteNote.lastModified) <= 1) {
                            // About the same note, just create mapping
                            mappingUpdates.add(remoteNote.getMapping(localNote.id, service))
                        } else if (localNote.modifiedDate > remoteNote.lastModified) {
                            // Local note is newer, update remote
                            logDebug("Local note (${localNote.title}) is newer ${localNote.modifiedDate}, ${remoteNote.lastModified}")
                            remoteUpdates.add(NoteAction.Update(localNote, remoteNote))
                        } else if (localNote.modifiedDate < remoteNote.lastModified) {
                            // Remote note is newer, update local
                            logDebug("Remote note(${remoteNote.title}) is newer ${remoteNote.lastModified}, ${localNote.modifiedDate}")
                            localUpdates.add(NoteAction.Update(localNote, remoteNote))
                        }
                        // If equal, no action needed
                    } else {
                        // No remote note with this title, create a new remote note
                        val syncNote = SyncNote(
                            id = 0, // Empty ID for new remote notes
                            idStr = "", content = localNote.content,
                            title = localNote.title, lastModified = localNote.modifiedDate
                        )
                        logDebug("New local note: ${localNote.title}")
                        remoteUpdates.add(NoteAction.Create(localNote, syncNote))
                    }
                }

                // Process remote notes
                for (remoteNote in remoteNotes) {
                    val localNote = localNotesMap[remoteNote.title]

                    if (localNote == null) {
                        // No local note with this title, create a new local note
                        val newLocalNote = Note(
                            title = remoteNote.title, modifiedDate = remoteNote.lastModified
                        )
                        logDebug("New Remote note: ${remoteNote.title}")
                        localUpdates.add(NoteAction.Create(newLocalNote, remoteNote))
                    }
                    // If the local note exists, it was already handled in the local notes loop
                }
            }
        }
        return SyncNotesResult(localUpdates, remoteUpdates, mappingUpdates)
    }
}

sealed interface NoteAction {
    data class Create(val note: Note, val remoteNote: SyncNote) : NoteAction
    data class Update(val note: Note, val remoteNote: SyncNote) : NoteAction
    data class Delete(val note: Note, val remoteNote: SyncNote) : NoteAction
}

data class SyncNotesResult(
    val localUpdates: List<NoteAction>,
    val remoteUpdates: List<NoteAction>,
    val newMappings: List<IdMapping> = emptyList()
)
