package org.qosp.notes.data.sync.nextcloud.model

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.nextcloud.NextcloudNote

fun Note.asNextcloudNote(id: Long, category: String): NextcloudNote = NextcloudNote(
    id = id,
    title = title,
    content = toStorableContent(),
    category = category,
    favorite = isPinned,
    modified = modifiedDate
)
