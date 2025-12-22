package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.Note

// Sealed class to represent remote operations
sealed class RemoteOperation {
    data class Create(val note: Note, val import: Boolean = false) : RemoteOperation()
    data class Update(val note: Note) : RemoteOperation()
    data class Delete(val note: Note) : RemoteOperation()
}

enum class SyncMethod {
    MAPPING,
    TITLE,
}

data class SyncNote(
    val id: Long,
    val idStr: String,
    val content: String?,
    val title: String,
    val lastModified: Long, // Epoch seconds
    val extra: String? = null,
    val category: String = "",
    val favorite: Boolean? = null,
    val readOnly: Boolean = false,
)
