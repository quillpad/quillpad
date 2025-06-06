package org.qosp.notes.data.sync.neu

import org.qosp.notes.data.sync.core.NextcloudNote

data class NewSyncNote(
    val id: Long,
    val idStr: String,
    val content: String?,
    val title: String,
    val lastModified: Long,
    val extra: String? = null,
    val category: String = "",
    val favorite: Boolean = false,
    val readOnly: Boolean = false,
) {
    fun asNextCloudNote(): NextcloudNote = NextcloudNote(
        id = id,
        content = content,
        title = title,
        modified = lastModified,
        etag = extra,
        category = category,
        favorite = favorite,
        readOnly = readOnly,
        remoteId = "",
    )
}

fun NextcloudNote.asNewSyncNote(): NewSyncNote = NewSyncNote(
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

data class RemoteNoteMetaData(
    val id: String,
    val title: String,
    val lastModified: Long,
)
