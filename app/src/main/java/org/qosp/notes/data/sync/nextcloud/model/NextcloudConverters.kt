package org.qosp.notes.data.sync.nextcloud.model

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.NextcloudNote

fun Note.asNextcloudNote(id: Long, category: String): NextcloudNote = NextcloudNote(
    id = id,
    title = title,
    content = if (isList) taskListToMd() else content,
    category = category,
    favorite = isPinned,
    modified = modifiedDate
)

fun NextcloudNote.asNewLocalNote(id: Long, notebookId: Long?) = Note(
    id = id,
    title = title,
    content = content ?: "",
    isPinned = favorite,
    modifiedDate = modified,
    notebookId = notebookId
)
