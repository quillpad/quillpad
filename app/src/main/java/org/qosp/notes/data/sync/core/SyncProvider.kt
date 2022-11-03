package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.Note

interface SyncProvider {
    suspend fun sync(config: ProviderConfig): BaseResult
    suspend fun createNote(note: Note, config: ProviderConfig): BaseResult
    suspend fun deleteNote(note: Note, config: ProviderConfig): BaseResult
    suspend fun updateNote(note: Note, config: ProviderConfig): BaseResult

    suspend fun moveNoteToBin(note: Note, config: ProviderConfig): BaseResult
    suspend fun restoreNote(note: Note, config: ProviderConfig): BaseResult

    suspend fun authenticate(config: ProviderConfig): BaseResult
    suspend fun isServerCompatible(config: ProviderConfig): BaseResult
}
