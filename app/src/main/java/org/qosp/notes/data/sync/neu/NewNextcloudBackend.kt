package org.qosp.notes.data.sync.neu

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.NextcloudNote
import org.qosp.notes.data.sync.core.ServerNotSupportedException
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import org.qosp.notes.data.sync.nextcloud.NextcloudBackend.Companion.MIN_SUPPORTED_VERSION
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.data.sync.nextcloud.createNote
import org.qosp.notes.data.sync.nextcloud.deleteNote
import org.qosp.notes.data.sync.nextcloud.getNote
import org.qosp.notes.data.sync.nextcloud.getNotes
import org.qosp.notes.data.sync.nextcloud.getNotesCapabilities
import org.qosp.notes.data.sync.nextcloud.model.asNextcloudNote
import org.qosp.notes.data.sync.nextcloud.updateNote
import org.qosp.notes.preferences.CloudService

class NewNextcloudBackend(
    private val api: NextcloudAPI,
    private val config: NextcloudConfig
) : INewSyncBackend {

    override val type: CloudService = CloudService.NEXTCLOUD

    override suspend fun createNote(note: Note): NewSyncNote = api.createNote(
        NextcloudNote(
            id = 0L, // ID will be assigned by the server
            content = note.content,
            title = note.title,
            category = note.notebookId?.toString() ?: "",
            favorite = note.isPinned,
            modified = note.modifiedDate,
            readOnly = null
        ), config
    ).asNewSyncNote()

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        val nNote = note.asNextcloudNote(mapping.remoteNoteId, "")
        val updatedNote = api.updateNote(nNote, mapping.extras ?: "", config)
        return mapping.copy(remoteNoteId = updatedNote.id, extras = updatedNote.etag)
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean = try {
        // Delete the note on the server
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        api.deleteNote(mapping.remoteNoteId, config)
        true
    } catch (_: Exception) {
        false
    }

    override suspend fun getNote(mapping: IdMapping): NewSyncNote? {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        return api.getNote(mapping.remoteNoteId, config).asNewSyncNote()
    }

    override suspend fun getAll(): List<NewSyncNote> {
        return api.getNotes(config).map { note -> note.asNewSyncNote() }
    }

    override suspend fun validateConfig(): BackendValidationResult {
        val result = runCatching {
            val capabilities = api.getNotesCapabilities(config)!!
            val maxServerVersion = capabilities.apiVersion.last().toFloat()
            if (MIN_SUPPORTED_VERSION.toFloat() > maxServerVersion) throw ServerNotSupportedException
        }
        return when (result.exceptionOrNull()) {
            null -> BackendValidationResult.Success
            is ServerNotSupportedException -> BackendValidationResult.Incompatible
            else -> BackendValidationResult.InvalidConfig
        }
    }
}
