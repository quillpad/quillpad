package org.qosp.notes.data.sync.nextcloud.model

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.nextcloud.NextcloudNote

fun Note.asNextcloudNote(id: Long, category: String): NextcloudNote {
    val baseContent = toStorableContent()
    // Embed customSortOrder as HTML comment at end of content so it syncs but isn't visible
    val contentWithMetadata = if (customSortOrder > 0) {
        "$baseContent\n<!-- customSortOrder:$customSortOrder -->"
    } else {
        baseContent
    }
    
    return NextcloudNote(
        id = id,
        title = title,
        content = contentWithMetadata,
        category = category,
        favorite = isPinned,
        modified = modifiedDate
    )
}
