package org.qosp.notes.data.sync.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.qosp.notes.data.model.Note
import org.qosp.notes.di.SyncScope

class SyncManager(val syncingScope: SyncScope) : KoinComponent {

    val syncProvider: StateFlow<ISyncProvider?> = MutableStateFlow(null)

    private suspend inline fun sendMessage(crossinline block: suspend () -> Message) = block().deferred.await()

    suspend fun updateNote(note: Note) = sendMessage { UpdateNote(note) }

    suspend fun updateOrCreate(note: Note) = sendMessage { UpdateOrCreateNote(note) }

}

private sealed class Message {
    val deferred: CompletableDeferred<BaseResult> = CompletableDeferred()
    override fun toString(): String = this::class.java.simpleName
}

private class CreateNote(val note: Note) : Message()
private class UpdateNote(val note: Note) : Message()
private class UpdateOrCreateNote(val note: Note) : Message()
private class DeleteNote(val note: Note) : Message()
private class Sync : Message()
private class Authenticate : Message()
private class IsServerCompatible : Message()
