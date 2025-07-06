package org.qosp.notes.data.sync

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.NextcloudNote

fun NextcloudNote.asSyncNote() = SyncNote(
    id = id,
    idStr = "",
    content = content,
    title = title,
    lastModified = modified, // Nextcloud already uses epoch seconds.
    extra = etag,
    category = category,
    favorite = favorite,
    readOnly = readOnly == true,
)

// Convert SyncNote to local Note with full content
fun SyncNote.toLocalNote() = Note(
    id = 0L, // Will be assigned by a database
    title = title,
    content = content ?: "",
    isPinned = favorite,
    modifiedDate = lastModified,
    notebookId = null, // TODO: Handle category to notebook conversion if needed
    isMarkdownEnabled = true // Default to Markdown enabled
)


fun SyncNote.getMapping(noteId: Long, syncProvider: ISyncBackend) = IdMapping(
    localNoteId = noteId,
    remoteNoteId = id,
    provider = syncProvider.type,
    extras = extra,
    isDeletedLocally = false,
    storageUri = idStr
)
