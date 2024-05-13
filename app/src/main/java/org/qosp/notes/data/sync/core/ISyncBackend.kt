package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService

interface ISyncBackend<T : ProviderConfig, N: SyncNote> {
    val service: CloudService

    suspend fun getSyncNoteFrom(note: Note, idMapping: IdMapping): N

    //    suspend fun sync(config: T): BaseResult
    suspend fun list(config: T): SyncResult<List<SyncNote>>
    suspend fun createNote(note: Note, config: T): SyncResult<N>
    suspend fun getNoteContent(note: N, config: T): SyncResult<N>
    suspend fun deleteNote(note: N, config: T): SyncResult<Unit>
    suspend fun updateNote(note: N, config: T): SyncResult<Unit>

    suspend fun authenticate(config: T): BaseResult
    suspend fun isServerCompatible(config: T): BaseResult
}

sealed class SyncResult<T> {
    data class Success<T>(val data: T) : SyncResult<T>()
    data class Error<T>(val error: Throwable) : SyncResult<T>()
}
