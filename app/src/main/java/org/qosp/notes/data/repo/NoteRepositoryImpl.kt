package org.qosp.notes.data.repo

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.msoul.datastore.defaultOf
import org.qosp.notes.Config
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.dao.ReminderDao
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteEntity
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.GenericError
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.NoteAction
import org.qosp.notes.data.sync.core.RemoteNoteMetaData
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.core.SynchronizeNotes
import org.qosp.notes.data.sync.toLocalNote
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.SortMethod
import java.time.Instant

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val idMappingDao: IdMappingDao,
    private val reminderDao: ReminderDao,
    private val backendProvider: BackendProvider,
    private val synchronizeNotes: SynchronizeNotes,
    private val syncingScope: SyncScope
) : NoteRepository {

    private val tag = NoteRepositoryImpl::class.java.simpleName

    // Map to track pending remote update jobs by noteId
    private val pendingUpdateJobs = mutableMapOf<Long, Job>()

    private suspend fun cleanMappingsForLocalNotes(vararg notes: Note) {
        val n = notes.filter { it.isLocalOnly }
        Log.d(tag, "cleanMappingsForLocalNotes: Cleaning ${n.size} local-only notes from ${notes.size} total")
        idMappingDao.setNotesToBeDeleted(*n.map { it.id }.toLongArray())
    }

    override suspend fun syncNotes(): BaseResult {
        Log.d(tag, "syncNotes: Starting synchronization")

        val syncProvider = backendProvider.syncProvider.value
        if (syncProvider == null || !backendProvider.isSyncing) {
            Log.d(tag, "syncNotes: Sync not available or disabled")
            return Success
        }

        try {
            // Get all local notes (excluding local-only ones)
            val localNotes = getAll().first().filterNot { it.isLocalOnly || it.isDeleted }
            Log.d(tag, "syncNotes: Found ${localNotes.size} local notes to sync")

            // Get all remote notes and convert to metadata
            val allRemoteNotes = syncProvider.getAll()
            val remoteNotes = allRemoteNotes.map { it.toRemoteNoteMetaData(syncProvider.type) }
            Log.d(tag, "syncNotes: Found ${remoteNotes.size} remote notes")

            // Use SynchronizeNotes to determine what updates are needed
            val syncResult = synchronizeNotes(localNotes, remoteNotes, syncProvider.type)
            Log.d(
                tag,
                "syncNotes: ${syncResult.localUpdates.size} local updates, ${syncResult.remoteUpdates.size} remote updates"
            )

            applyLocalUpdates(syncResult.localUpdates, syncProvider, allRemoteNotes)
            applyRemoteUpdates(syncResult.remoteUpdates)
            Log.d(tag, "syncNotes: Synchronization completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "syncNotes: Synchronization failed: ${e.message}", e)
            return GenericError(e.message ?: "Unknown error")
        }
        return Success
    }

    private suspend fun applyLocalUpdates(
        localUpdates: List<NoteAction>,
        syncProvider: ISyncBackend,
        remoteNotes: List<SyncNote>
    ) {
        if (localUpdates.isEmpty()) return

        // Create a map for quick lookup of remote notes by ID
        val remoteNotesMap = remoteNotes.associateBy {
            when (syncProvider.type) {
                CloudService.NEXTCLOUD -> it.id.toString()
                CloudService.FILE_STORAGE -> it.idStr
                else -> it.idStr
            }
        }

        for (action in localUpdates) {
            try {
                when (action) {
                    is NoteAction.Create -> {
                        val syncNote = remoteNotesMap[action.remoteNoteMetaData.id] ?: continue
                        val noteId = insertNote(syncNote.toLocalNote(), sync = false)
                        idMappingDao.insert(syncNote.getMapping(noteId, syncProvider))
                    }

                    is NoteAction.Update -> {
                        val syncNote = remoteNotesMap[action.remoteNoteMetaData.id] ?: continue
                        val note = syncNote.toLocalNote().copy(id = action.note.id)
                        updateNote(note, sync = false)
                    }

                    is NoteAction.Delete -> {
                        deleteNotes(action.note, sync = false)
                        idMappingDao.deleteByLocalId(action.note.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "applyLocalUpdates: Failed to apply action $action: ${e.message}")
            }
        }
    }

    private fun applyRemoteUpdates(remoteUpdates: List<NoteAction>) {
        if (remoteUpdates.isEmpty()) return
        for (action in remoteUpdates) {
            try {
                when (action) {
                    is NoteAction.Create -> insertRemoteNote(action.note, action.note.id)
                    is NoteAction.Update -> updateRemoteNote(action.note)
                    is NoteAction.Delete -> deleteRemoteNotes(listOf(action.note))
                }
            } catch (e: Exception) {
                Log.e(tag, "applyRemoteUpdates: Failed to apply action $action: ${e.message}")
            }
        }
    }

    override suspend fun insertNote(note: Note, sync: Boolean): Long {
        Log.d(tag, "insertNote: Creating note '${note.title}', isLocalOnly=${note.isLocalOnly}")
        val noteId = noteDao.insert(note.toEntity())
        if (note.isLocalOnly.not() && backendProvider.isSyncing && sync) insertRemoteNote(note, noteId)
        return noteId
    }

    private fun insertRemoteNote(note: Note, noteId: Long) {
        backendProvider.syncProvider.value?.let { syncProvider ->
            syncingScope.launch {
                try {
                    val created = syncProvider.createNote(note)
                    idMappingDao.insert(created.getMapping(noteId, syncProvider))
                    noteDao.updateLastModified(noteId, created.lastModified)
                    Log.d(tag, "insertNote: Synced note to ${syncProvider.type}, local ID=$noteId")
                } catch (e: Exception) {
                    Log.e(tag, "insertNote: Sync failed for note ID=$noteId: ${e.message}")
                }
            }
        }
    }

    private suspend fun updateNote(note: Note, sync: Boolean) {
        Log.d(tag, "updateNote: Updating note ID=${note.id}, title='${note.title}'")
        noteDao.update(note.toEntity())
        if (note.isLocalOnly.not() && backendProvider.isSyncing && sync) updateRemoteNote(note)
    }

    private fun updateRemoteNote(note: Note) {
        backendProvider.syncProvider.value?.let { syncProvider ->
            // Cancel any existing job for this noteId
            pendingUpdateJobs[note.id]?.cancel()

            // Create a new job with debouncing
            val job = syncingScope.launch {
                try {
                    delay(Config.RemoteUpdateDebounceTime)

                    // Get the existing mapping for this note
                    val mapping = idMappingDao.getByLocalIdAndProvider(note.id, syncProvider.type)
                    if (mapping != null) {
                        // Update the note on the remote backend
                        val updatedMapping = syncProvider.updateNote(note, mapping)
                        idMappingDao.update(updatedMapping)
                        Log.d(tag, "updateNote: Successfully synced update to ${syncProvider.type}")
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    Log.e(tag, "Failed to sync note update: ${e.message}", e)
                } finally {
                    // Remove the job from the map when it's done
                    pendingUpdateJobs.remove(note.id)
                }
            }

            // Store the job in the map
            pendingUpdateJobs[note.id] = job
        }
    }

    override suspend fun updateNotes(vararg notes: Note, sync: Boolean) = notes.forEach { updateNote(it, sync) }

    override suspend fun moveNotesToBin(vararg notes: Note) {
        Log.d(tag, "moveNotesToBin: Moving ${notes.size} notes to bin")
        val entities = notes.map { it.toEntity().copy(isDeleted = true, deletionDate = Instant.now().epochSecond) }
            .toTypedArray<NoteEntity>()

        noteDao.update(*entities)
        reminderDao.deleteIfNoteIdIn(notes.map { it.id })
        cleanMappingsForLocalNotes(*notes)
        deleteRemoteNotes(notes.toList())
    }

    private fun deleteRemoteNotes(notes: List<Note>) {
        if (backendProvider.isSyncing) {
            backendProvider.syncProvider.value?.let { syncProvider ->
                syncingScope.launch {
                    val noteIds = notes.filterNot { it.isLocalOnly }.map { it.id }.toLongArray()
                    val mappings = idMappingDao.getByLocalIds(*noteIds).filter { it.provider == syncProvider.type }
                    Log.d(tag, "deleteRemoteNotes: Deleting ${mappings.size} remote notes from ${syncProvider.type}")
                    mappings.forEach { syncProvider.deleteNote(it) }
                    idMappingDao.deleteByLocalId(*noteIds)
                }
            }
        }
    }

    override suspend fun restoreNotes(vararg notes: Note) {
        Log.d(tag, "restoreNotes: Restoring ${notes.size} notes from bin")
        val array = notes
            .map { it.toEntity().copy(isDeleted = false, deletionDate = null) }
            .toTypedArray()
        noteDao.update(*array)
        cleanMappingsForLocalNotes(*notes)
        if (backendProvider.isSyncing) {
            backendProvider.syncProvider.value?.let { syncProvider ->
                syncingScope.launch {
                    val syncableNotes = notes.filterNot { it.isLocalOnly }
                    Log.d(tag, "restoreNotes: Re-syncing ${syncableNotes.size} restored notes to ${syncProvider.type}")
                    syncableNotes
                        .associateWith { syncProvider.createNote(it) }
                        .forEach { (n, syncNote) ->
                            idMappingDao.insert(syncNote.getMapping(n.id, syncProvider))
                            noteDao.updateLastModified(n.id, syncNote.lastModified)
                        }
                }
            }
        }
    }

    override suspend fun deleteNotes(vararg notes: Note, sync: Boolean) {
        Log.d(tag, "deleteNotes: Permanently deleting ${notes.size} notes")
        val array = notes.map { it.toEntity() }.toTypedArray()
        noteDao.delete(*array)
        if (sync) deleteRemoteNotes(notes.toList())
    }

    override suspend fun discardEmptyNotes(): Boolean {
        val notes = noteDao.getAllBlankTitleNotes().first().filter { it.isEmpty() }.toTypedArray()
        Log.d(tag, "discardEmptyNotes: Found ${notes.size} empty notes to discard")
        deleteNotes(*notes)
        return notes.isNotEmpty()
    }

    override suspend fun permanentlyDeleteNotesInBin() {
        val noteIds = noteDao.getDeleted(defaultOf()).first().map { it.id }.toLongArray()
        Log.d(tag, "permanentlyDeleteNotesInBin: Permanently deleting ${noteIds.size} notes from bin")
        idMappingDao.deleteByLocalId(*noteIds)
        noteDao.permanentlyDeleteNotesInBin()
    }

    override fun getById(noteId: Long): Flow<Note?> {
        return noteDao.getById(noteId)
    }

    override fun getDeleted(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getDeleted(sortMethod)
    }

    override fun getArchived(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getArchived(sortMethod)
    }

    override fun getNonDeleted(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getNonDeleted(sortMethod)
    }

    override fun getNonDeletedOrArchived(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getNonDeletedOrArchived(sortMethod)
    }

    override fun getAll(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getAll(sortMethod)
    }

    override fun getByNotebook(notebookId: Long, sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getByNotebook(notebookId, sortMethod)
    }

    override fun getNonRemoteNotes(provider: CloudService, sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getNonRemoteNotes(sortMethod, provider)
    }

    override fun getNotesWithoutNotebook(sortMethod: SortMethod): Flow<List<Note>> {
        return noteDao.getNotesWithoutNotebook(sortMethod)
    }

    override suspend fun getNotesByCloudService(provider: CloudService): Map<IdMapping, Note?> {
        val allNotes = getAll().first().associateBy { it.id }
        val mappings = idMappingDao.getAllByCloudService(provider)
        Log.d(tag, "getNotesByCloudService: Found ${mappings.size} mappings for $provider")
        return mappings.associateWith { allNotes[it.localNoteId] }
    }
}

private fun SyncNote.getMapping(noteId: Long, syncProvider: ISyncBackend) = IdMapping(
    localNoteId = noteId,
    remoteNoteId = id,
    provider = syncProvider.type,
    extras = extra,
    isDeletedLocally = false,
    storageUri = idStr
)

private fun SyncNote.toRemoteNoteMetaData(cloudService: CloudService): RemoteNoteMetaData {
    val remoteId = when (cloudService) {
        CloudService.NEXTCLOUD -> id.toString()
        CloudService.FILE_STORAGE -> idStr
        else -> idStr
    }
    return RemoteNoteMetaData(
        id = remoteId,
        title = title,
        lastModified = lastModified
    )
}
