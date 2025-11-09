package org.qosp.notes.data.sync

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.NextcloudNote
import org.qosp.notes.preferences.CloudService

fun NextcloudNote.asSyncNote() = SyncNote(
    id = id,
    idStr = id.toString(),
    content = content,
    title = title,
    lastModified = modified, // Nextcloud already uses epoch seconds.
    extra = etag,
    category = category,
    favorite = favorite,
    readOnly = readOnly == true,
)

// Convert SyncNote to local Note with full content
fun SyncNote.toLocalNote(): Note {
    val contentStr = content ?: ""
    
    // Extract customSortOrder from HTML comment if present
    val sortOrderRegex = Regex("""<!-- customSortOrder:(\d+) -->""")
    val match = sortOrderRegex.find(contentStr)
    val customSortOrder = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    
    // Remove metadata comment from content
    val cleanContent = contentStr.replace(sortOrderRegex, "").trim()
    
    return Note(
        id = 0L, // Will be assigned by a database
        title = title,
        content = cleanContent,
        isPinned = favorite,
        modifiedDate = lastModified,
        notebookId = null, // TODO: Handle category to notebook conversion if needed
        isMarkdownEnabled = true, // Default to Markdown enabled
        customSortOrder = customSortOrder
    )
}

fun SyncNote.getMapping(noteId: Long, service: CloudService) = IdMapping(
    localNoteId = noteId,
    remoteNoteId = id,
    provider = service,
    extras = extra,
    isDeletedLocally = false,
    storageUri = idStr
)
