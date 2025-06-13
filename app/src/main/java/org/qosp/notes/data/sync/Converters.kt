package org.qosp.notes.data.sync

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.neu.NewSyncNote
import org.qosp.notes.data.sync.nextcloud.NextcloudNote

fun NextcloudNote.asNewSyncNote() = NewSyncNote(
    id = id,
    idStr = "",
    content = content,
    title = title,
    lastModified = modified,
    extra = etag,
    category = category,
    favorite = favorite,
    readOnly = readOnly == true,
)

// Convert NewSyncNote to local Note with full content
fun NewSyncNote.toLocalNote() = Note(
    id = 0L, // Will be assigned by database
    title = title,
    content = content ?: "",
    isPinned = favorite,
    modifiedDate = lastModified,
    notebookId = null, // TODO: Handle category to notebook conversion if needed
    isMarkdownEnabled = true // Default to markdown enabled
)
