package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qosp.notes.data.dao.NoteTagDao
import org.qosp.notes.data.dao.TagDao
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.Tag
import org.qosp.notes.data.sync.core.SyncManager

class TagRepository(
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao,
    private val noteRepository: NoteRepository,
    private val syncManager: SyncManager,
) {

    fun getAll(): Flow<List<Tag>> {
        return tagDao.getAll()
    }

    fun getById(tagId: Long): Flow<Tag?> {
        return tagDao.getById(tagId)
    }

    fun getByNoteId(noteId: Long): Flow<List<Tag>> {
        return noteTagDao.getByNoteId(noteId)
    }

    fun getByName(name: String): Flow<Tag?> {
        return tagDao.getByName(name)
    }

    suspend fun insert(tag: Tag): Long {
        return tagDao.insert(tag)
    }

    suspend fun delete(vararg tags: Tag, shouldSync: Boolean = true) {
        val affectedNotes = tags
            .map { noteTagDao.getNotesByTagId(it.id).first() }
            .flatten()
            .filterNot { it.isLocalOnly }

        tagDao.delete(*tags)

        if (shouldSync) {
            syncManager.syncingScope.launch {
                affectedNotes.forEach { syncManager.updateNote(it) }
            }
        }
    }

    suspend fun update(vararg tags: Tag) {
        tagDao.update(*tags)
    }

    suspend fun addTagToNote(tagId: Long, noteId: Long, shouldSync: Boolean = true) {
        noteTagDao.insert(NoteTagJoin(tagId, noteId))

        if (shouldSync) {
            syncManager.syncingScope.launch {
                val note = noteRepository.getById(noteId).first()?.takeUnless { it.isLocalOnly } ?: return@launch
                syncManager.updateNote(note)
            }
        }
    }

    suspend fun deleteTagFromNote(tagId: Long, noteId: Long, shouldSync: Boolean = true) {
        noteTagDao.delete(NoteTagJoin(tagId, noteId))

        if (shouldSync) {
            syncManager.syncingScope.launch {
                val note = noteRepository.getById(noteId).first()?.takeUnless { it.isLocalOnly } ?: return@launch
                syncManager.updateNote(note)
            }
        }
    }

    suspend fun copyTags(fromNoteId: Long, toNoteId: Long) {
        noteTagDao.copyTags(fromNoteId, toNoteId)
    }
}
