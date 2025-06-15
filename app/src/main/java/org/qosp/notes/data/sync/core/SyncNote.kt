package org.qosp.notes.data.sync.core

import org.qosp.notes.data.model.IdMapping

data class SyncNote(
    val id: Long,
    val idStr: String,
    val content: String?,
    val title: String,
    val lastModified: Long,
    val extra: String? = null,
    val category: String = "",
    val favorite: Boolean = false,
    val readOnly: Boolean = false,
)


fun SyncNote.getMapping(noteId: Long, syncProvider: ISyncBackend) = IdMapping(
    localNoteId = noteId,
    remoteNoteId = id,
    provider = syncProvider.type,
    extras = extra,
    isDeletedLocally = false,
    storageUri = idStr
)
