package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import me.msoul.datastore.defaultOf
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.SortMethod

interface NoteRepository {
    suspend fun insertNote(note: Note, sync: Boolean = true): Long
    suspend fun updateNotes(vararg notes: Note, sync: Boolean = true)
    suspend fun moveNotesToBin(vararg notes: Note, sync: Boolean = true)
    suspend fun restoreNotes(vararg notes: Note)
    suspend fun deleteNotes(vararg notes: Note, sync: Boolean = true)
    suspend fun discardEmptyNotes(): Boolean
    suspend fun permanentlyDeleteNotesInBin()

    suspend fun syncNotes(): BaseResult
    fun getById(noteId: Long): Flow<Note?>
    fun getDeleted(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getArchived(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getNonDeleted(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getNonDeletedOrArchived(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getAll(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getByNotebook(notebookId: Long, sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getNonRemoteNotes(provider: CloudService, sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    fun getNotesWithoutNotebook(sortMethod: SortMethod = defaultOf()): Flow<List<Note>>
    suspend fun getNotesByCloudService(provider: CloudService): Map<IdMapping, Note?>
    suspend fun deleteIdMappingsForCloudService(cloudService: CloudService)
}
