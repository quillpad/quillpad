package org.qosp.notes.data.repo

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.msoul.datastore.defaultOf
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.dao.ReminderDao
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteEntity
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.GenericError
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.NoteAction
import org.qosp.notes.data.sync.core.ProcessRemoteActions
import org.qosp.notes.data.sync.core.RemoteOperation.Create
import org.qosp.notes.data.sync.core.RemoteOperation.Delete
import org.qosp.notes.data.sync.core.RemoteOperation.Update
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncMethod
import org.qosp.notes.data.sync.core.SyncNotesResult
import org.qosp.notes.data.sync.core.SynchronizeNotes
import org.qosp.notes.data.sync.getMapping
import org.qosp.notes.data.sync.toLocalNote
import org.qosp.notes.data.sync.updateLocalNote
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.ui.utils.Toaster
import java.time.Instant

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val idMappingDao: IdMappingDao,
    private val reminderDao: ReminderDao,
    private val backendProvider: BackendProvider,
    private val synchronizeNotes: SynchronizeNotes,
    private val processRemoteActions: ProcessRemoteActions,
    private val syncingScope: SyncScope,
    private val toaster: Toaster
) : NoteRepository {

    private val tag = NoteRepositoryImpl::class.java.simpleName

    private suspend fun cleanMappingsForLocalNotes(vararg notes: Note) {
        val n = notes.filter { it.isLocalOnly }
        Log.d(tag, "cleanMappingsForLocalNotes: Cleaning ${n.size} local-only notes from ${notes.size} total")
        idMappingDao.setNotesToBeDeleted(*n.map { it.id }.toLongArray())
    }

    override suspend fun syncNotes(): BaseResult {
        Log.d(tag, "syncNotes: Starting synchronization")

        val syncProvider = backendProvider.syncProvider.value
        if (syncProvider == null || !backendProvider.isSyncing) {
            Log.i(tag, "syncNotes: Sync not available or disabled")
            return Success
        }

        // Check backend availability before proceeding
        when (val status = syncProvider.isAvailable()) {
            is AvailabilityStatus.Available -> {
                Log.d(tag, "syncNotes: Backend is available")
            }

            is AvailabilityStatus.Unavailable -> {
                Log.w(tag, "syncNotes: Backend unavailable - ${status.reason}")
                toaster.showLong(status.reason)
                return GenericError(status.reason)
            }
        }

        val syncMethod =
            if (idMappingDao.getCountByCloudService(syncProvider.type) == 0)
                SyncMethod.TITLE else SyncMethod.MAPPING
        try {
            // Get all local notes (excluding local-only ones)
            val localNotes = getAll().first().filterNot { it.isLocalOnly || it.isDeleted }

            // Get all remote notes and convert to metadata
            val allRemoteNotes = syncProvider.getAll() ?: return GenericError("Failed to fetch remote notes")
            Log.d(
                tag, "syncNotes: Syncing by $syncMethod. " +
                    "Found ${allRemoteNotes.size} remote notes, and ${localNotes.size} local notes"
            )

            // Use SynchronizeNotes to determine what updates are needed
            val syncResult =
                synchronizeNotes(localNotes, allRemoteNotes, service = syncProvider.type, syncMethod)
            Log.d(tag, "sync updates: ${syncResult.localUpdates.size} local, ${syncResult.remoteUpdates.size} remote")

            if (syncMethod == SyncMethod.TITLE) applyMappingChanges(syncResult, syncProvider) // Initial import
            applyLocalUpdates(syncResult.localUpdates, syncProvider)
            applyRemoteUpdates(syncResult.remoteUpdates)
            Log.i(tag, "syncNotes: Synchronization completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "syncNotes: Synchronization failed: ${e.message}", e)
            return GenericError(e.message ?: "Unknown error")
        }
        return Success
    }

    private suspend fun applyLocalUpdates(localUpdates: List<NoteAction>, syncProvider: ISyncBackend) {
        for (action in localUpdates) {
            try {
                when (action) {
                    is NoteAction.Create -> {
                        val syncNote = action.remoteNote
                        val noteId = insertNote(syncNote.toLocalNote(defaultPinned = false), sync = false)
                        idMappingDao.insert(syncNote.getMapping(noteId, syncProvider.type))
                    }

                    is NoteAction.Update -> {
                        val mergedNote = action.remoteNote.updateLocalNote(action.note)
                        val note = if (action.note.isList) {
                            val tasks = mergedNote.mdToTaskList(mergedNote.content)
                            mergedNote.copy(content = "", taskList = tasks, isList = true)
                        } else {
                            mergedNote
                        }
                        idMappingDao.updateNoteExtras(
                            localId = action.note.id,
                            cloudService = syncProvider.type,
                            extras = action.remoteNote.extra
                        )
                        updateNote(note, sync = false)
                    }

                    is NoteAction.Delete -> {
                        moveNotesToBin(action.note, sync = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "applyLocalUpdates: Failed to apply action $action: ${e.message}")
            }
        }
    }

    private fun applyRemoteUpdates(remoteUpdates: List<NoteAction>) {
        for (action in remoteUpdates) {
            try {
                when (action) {
                    is NoteAction.Create -> processRemoteActions(action.note.id, Create(action.note))
                    is NoteAction.Update -> processRemoteActions(action.note.id, Update(action.note))
                    is NoteAction.Delete -> processRemoteActions(action.note.id, Delete(action.note))
                }
            } catch (e: Exception) {
                Log.e(tag, "applyRemoteUpdates: Failed to apply action $action: ${e.message}")
            }
        }
    }

    private suspend fun applyMappingChanges(syncResult: SyncNotesResult, syncProvider: ISyncBackend) {
        syncResult.newMappings.forEach { idMappingDao.insert(it) }
        syncResult.localUpdates.filterIsInstance<NoteAction.Update>().map {
            it.remoteNote.getMapping(it.note.id, syncProvider.type)
        }.forEach { idMappingDao.insert(it) }
        syncResult.remoteUpdates.filterIsInstance<NoteAction.Update>().map {
            it.remoteNote.getMapping(it.note.id, syncProvider.type)
        }.forEach { idMappingDao.insert(it) }
    }

    override suspend fun insertNote(note: Note, sync: Boolean): Long {
        Log.d(tag, "insertNote: Creating note '${note.title}', isLocalOnly=${note.isLocalOnly}")
        val noteId = noteDao.insert(note.toEntity())
        if (note.isLocalOnly.not() && backendProvider.isSyncing && sync) {
            val note1 = note.copy(id = noteId)
            processRemoteActions(note1.id, Create(note1))
        }
        return noteId
    }

    override suspend fun updateNotes(vararg notes: Note, sync: Boolean) = notes.forEach { updateNote(it, sync) }

    private suspend fun updateNote(note: Note, sync: Boolean) {
        Log.d(tag, "updateNote: Updating note ID=${note.id}, title='${note.title}'")
        noteDao.update(note.toEntity())
        if (note.isLocalOnly.not() && backendProvider.isSyncing && sync) {
            processRemoteActions(note.id, Update(note))
        }
    }

    override suspend fun moveNotesToBin(vararg notes: Note, sync: Boolean) {
        Log.d(tag, "moveNotesToBin: Moving ${notes.size} notes to bin")
        val entities = notes.map { it.toEntity().copy(isDeleted = true, deletionDate = Instant.now().epochSecond) }
            .toTypedArray<NoteEntity>()

        noteDao.update(*entities)
        reminderDao.deleteIfNoteIdIn(notes.map { it.id })
        cleanMappingsForLocalNotes(*notes)
        notes.filterNot { it.isLocalOnly }.forEach {
            if (sync) processRemoteActions(it.id, Delete(it))
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
                    try {
                        syncableNotes
                            .associateWith { syncProvider.createNote(it) }
                            .forEach { (n, syncNote) ->
                                idMappingDao.insert(syncNote.getMapping(n.id, syncProvider.type))
                                noteDao.updateLastModified(n.id, syncNote.lastModified)
                            }
                    } catch (e: Exception) {
                        Log.e(tag, "restoreNotes: Error re-syncing restored notes", e)
                    }
                }
            }
        }
    }

    override suspend fun deleteNotes(vararg notes: Note, sync: Boolean) {
        Log.d(tag, "deleteNotes: Permanently deleting ${notes.size} notes")
        val array = notes.map { it.toEntity() }.toTypedArray()
        noteDao.delete(*array)
        if (sync) notes.filterNot { it.isLocalOnly }.forEach {
            processRemoteActions(it.id, Delete(it))
        }
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

    override suspend fun deleteIdMappingsForCloudService(cloudService: CloudService) =
        idMappingDao.deleteAllMappingsFor(cloudService)
}
