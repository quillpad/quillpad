package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService

interface ISyncProvider {
    val service: CloudService
    fun getConfig(): ProviderConfig?
    suspend fun sync(): BaseResult
    suspend fun createNote(note: Note): BaseResult
    suspend fun deleteNote(note: Note): BaseResult
    suspend fun updateNote(note: Note): BaseResult

    suspend fun authenticate(config: ProviderConfig?): BaseResult
    suspend fun isServerCompatible(config: ProviderConfig?): BaseResult
}

