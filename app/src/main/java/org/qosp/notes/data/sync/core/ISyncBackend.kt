package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService

interface ISyncBackend {
    val type: CloudService
    suspend fun createNote(note: Note): SyncNote
    suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping
    suspend fun deleteNote(mapping: IdMapping): Boolean
    suspend fun getNote(mapping: IdMapping): SyncNote?
    suspend fun getAll(): List<SyncNote>?
}
