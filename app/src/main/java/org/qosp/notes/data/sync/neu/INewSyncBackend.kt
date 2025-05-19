package org.qosp.notes.data.sync.neu

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService

interface INewSyncBackend {
    val type: CloudService
    suspend fun createNote(note: Note): NewSyncNote
    suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping
    suspend fun deleteNote(mapping: IdMapping): Boolean
    suspend fun getNote(mapping: IdMapping): NewSyncNote?
    suspend fun getAll(): List<NewSyncNote>
    suspend fun validateConfig(): BackendValidationResult
}


data class RemoteNoteMetaData(
    val id: String,
    val title: String,
    val lastModified: Long,
)

sealed class BackendValidationResult {
    object Success : BackendValidationResult()
    object InvalidConfig : BackendValidationResult()
    object Incompatible : BackendValidationResult()
}
