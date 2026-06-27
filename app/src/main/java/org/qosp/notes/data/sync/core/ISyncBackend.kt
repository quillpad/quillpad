package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService

interface ISyncBackend {
    val type: CloudService

    /**
     * Check if the sync backend is available for operations.
     * Called before every sync to ensure the backend can be reached.
     * @return AvailabilityStatus indicating if backend is available or unavailable with reason
     */
    suspend fun isAvailable(): AvailabilityStatus

    suspend fun createNote(note: Note): SyncNote
    suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping
    suspend fun deleteNote(mapping: IdMapping): Boolean
    suspend fun getNote(mapping: IdMapping): SyncNote?
    suspend fun getAll(): List<SyncNote>?
}
