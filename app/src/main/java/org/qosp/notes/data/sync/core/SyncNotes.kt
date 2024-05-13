package org.qosp.notes.data.sync.core

import android.net.Uri
import kotlinx.serialization.Serializable
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.CloudService.FILE_STORAGE
import org.qosp.notes.preferences.CloudService.NEXTCLOUD

interface SyncNote {
    val content: String?
    val title: String
    val modified: Long
    fun getIdMappingFor(note: Note): IdMapping
}

@Serializable
data class NextcloudNote(
    val id: Long,
    val etag: String? = null,
    override val content: String?,
    override val title: String,
    val category: String,
    val favorite: Boolean,
    override val modified: Long,
    val readOnly: Boolean? = null,
) : SyncNote {
    override fun getIdMappingFor(note: Note) = IdMapping(
        localNoteId = note.id,
        remoteNoteId = id,
        provider = NEXTCLOUD,
        extras = etag,
        isDeletedLocally = false,
    )
}

data class NoteFile(
    override val modified: Long,
    override val content: String?,
    override val title: String,
    val uri: Uri?
) : SyncNote {
    override fun getIdMappingFor(note: Note) = IdMapping(
        localNoteId = note.id,
        remoteNoteId = null,
        extras = null,
        isDeletedLocally = false,
        provider = FILE_STORAGE,
        storageUri = uri?.toString()
    )
}
