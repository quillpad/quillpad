package org.qosp.notes.data.sync.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.qosp.notes.Config
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.RemoteOperation.Create
import org.qosp.notes.data.sync.core.RemoteOperation.Delete
import org.qosp.notes.data.sync.core.RemoteOperation.Update
import org.qosp.notes.data.sync.getMapping
import org.qosp.notes.di.SyncScope
import org.qosp.notes.ui.utils.Toaster
import java.util.concurrent.ConcurrentHashMap

class ProcessRemoteActions(
    private val syncingScope: SyncScope,
    private val backendProvider: BackendProvider,
    private val idMappingDao: IdMappingDao,
    private val noteDao: NoteDao,
    private val toaster: Toaster,
) {
    private val tag = "ProcessRemoteActions"

    // Thread-safe map to store the latest operation for each noteId
    private val operationQueue = ConcurrentHashMap<Long, RemoteOperation>()

    // Map to track pending remote operation jobs by noteId
    private val pendingJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Process a remote operation for a note.
     * This method ensures that only the latest operation for each noteId is processed,
     * and earlier operations are ignored.
     */
    operator fun invoke(noteId: Long, operation: RemoteOperation) {
        if (!backendProvider.isSyncing) return // Not syncing now

        // Store the operation in the queue, replacing any existing operation for this noteId
        operationQueue[noteId] = operation

        if (pendingJobs[noteId] != null) return // If there's an existing job, let it finish.
        val job = syncingScope.launch { // Create a new job to process the operation
            var last: String = ""
            try {
                // Add debounce delay to allow for batching of rapid operations
                delay(Config.RemoteUpdateDebounceTime)

                // Check backend availability before processing
                val syncProvider = backendProvider.syncProvider.value ?: return@launch
                when (val status = syncProvider.isAvailable()) {
                    is AvailabilityStatus.Available -> { /* Continue */
                    }

                    is AvailabilityStatus.Unavailable -> {
                        Log.w(tag, "Backend unavailable for remote operation: ${status.reason}")
                        toaster.showLong(status.reason)
                        return@launch
                    }
                }

                // Get the latest operation for this noteId
                var latestAction = operationQueue.remove(noteId)
                while (latestAction != null) {
                    // Process the operation based on its type
                    when (latestAction) {
                        is Create -> {
                            last = latestAction.note.content.takeLast(4)
                            Log.d(tag, "insertRemoteNote: for note ID=$noteId ending ...$last")
                            backendProvider.syncProvider.value?.let { insertRemote(it, latestAction.note) }
                        }

                        is Update -> {
                            last = latestAction.note.content.takeLast(4)
                            Log.d(tag, "updateRemoteNote: for note ID=$noteId ending ...$last")
                            backendProvider.syncProvider.value?.let { updateRemote(latestAction.note, it) }
                        }

                        is Delete -> {
                            last = latestAction.note.content.takeLast(4)
                            Log.d(tag, "deleteRemoteNotes: for $noteId ending ...$last")
                            backendProvider.syncProvider.value?.let { deleteRemoteNotes(latestAction.note, it) }
                        }
                    }
                    latestAction = operationQueue.remove(noteId)
                }
            } catch (_: CancellationException) {
                // Job was canceled, which is expected when a newer operation comes in
            } catch (e: Exception) {
                Log.e(tag, "processRemoteOperation: Error processing operation: ${e.message}", e)
            } finally {
                // Remove the job from the maps when done
                pendingJobs.remove(noteId)
                Log.d(tag, "Completed ...$last")
            }
        }
        // Store the job in the map
        pendingJobs[noteId] = job
    }

    private suspend fun deleteRemoteNotes(note: Note, syncProvider: ISyncBackend) = try {
        idMappingDao.getByLocalIdAndProvider(note.id, syncProvider.type)?.let { mapping ->
            syncProvider.deleteNote(mapping = mapping)
            idMappingDao.deleteByLocalId(note.id)
        }
    } catch (e: Exception) {
        Log.e(tag, "processRemoteOperation: Failed to delete notes: ${e.message}", e)
    }

    private suspend fun insertRemote(syncProvider: ISyncBackend, note: Note) = try {
        val created = syncProvider.createNote(note)
        idMappingDao.insert(created.getMapping(note.id, syncProvider.type))
        noteDao.updateLastModified(note.id, created.lastModified)
    } catch (e: Exception) {
        Log.e(tag, "processRemoteOperation: Failed to create note ID=${note.id}: ${e.message}", e)
        toaster.showShort(e.message ?: "Failed to create note")
    }

    private suspend fun updateRemote(note: Note, syncProvider: ISyncBackend) = try {
        idMappingDao.getByLocalIdAndProvider(note.id, syncProvider.type)?.let {
            val updatedMapping = syncProvider.updateNote(note, it)
            idMappingDao.update(updatedMapping)
        }
    } catch (e: Exception) {
        Log.e(tag, "processRemoteOperation: Failed to update note ID=${note.id}: ${e.message}", e)
    }
}
