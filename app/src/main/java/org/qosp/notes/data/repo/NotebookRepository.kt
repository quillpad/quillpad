package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.dao.NotebookDao
import org.qosp.notes.data.model.Notebook

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val noteRepository: NoteRepository,
) {

    suspend fun insert(notebook: Notebook): Long {
        return notebookDao.insert(notebook)
    }

    suspend fun delete(vararg notebooks: Notebook, shouldSync: Boolean = true) {
        val affectedNotes = notebooks
            .map { noteRepository.getByNotebook(it.id).first() }
            .flatten()
            .filterNot { it.isLocalOnly }

        notebookDao.delete(*notebooks)

//        if (shouldSync && syncManager != null) {
//            syncManager.syncingScope.launch {
//                affectedNotes.forEach { syncManager.updateNote(it) }
//            }
//        }
    }

    suspend fun update(vararg notebooks: Notebook, shouldSync: Boolean = true) {
        notebookDao.update(*notebooks)

//        if (shouldSync && syncManager != null) {
//            syncManager.syncingScope.launch {
//                notebooks
//                    .map { noteRepository.getByNotebook(it.id).first() }
//                    .flatten()
//                    .filterNot { it.isLocalOnly }
//                    .forEach { syncManager.updateNote(it) }
//            }
//        }
    }

    fun getById(notebookId: Long): Flow<Notebook?> {
        return notebookDao.getById(notebookId)
    }

    fun getAll(): Flow<List<Notebook>> {
        return notebookDao.getAll()
    }

    fun getByName(name: String): Flow<Notebook?> {
        return notebookDao.getByName(name)
    }
}
