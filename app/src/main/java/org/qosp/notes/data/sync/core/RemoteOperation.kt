package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.Note

// Sealed class to represent remote operations
sealed class RemoteOperation {
    data class Create(val note: Note) : RemoteOperation()
    data class Update(val note: Note) : RemoteOperation()
    data class Delete(val note: Note) : RemoteOperation()
}
