package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import org.qosp.notes.BuildConfig
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.asSyncNote
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.model.asNextcloudNote
import org.qosp.notes.preferences.CloudService

class NextcloudBackend(
    private val apiProvider: NextcloudAPIProvider,
    private val config: NextcloudConfig
) : ISyncBackend {

    private val tag = javaClass.simpleName
    override val type: CloudService = CloudService.NEXTCLOUD

    override suspend fun createNote(note: Note): SyncNote {
        if (BuildConfig.DEBUG) Log.d(tag, "createNote() called with: note = ${note.title}")
        val api = apiProvider.getAPI()
        return api.createNote(note.asNextcloudNote(0, ""), config).asSyncNote()
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        if (BuildConfig.DEBUG) Log.d(tag, "updateNote: ${note.title}")
        val api = apiProvider.getAPI()
        val nNote = note.asNextcloudNote(mapping.remoteNoteId, "")
        val updatedNote = api.updateNote(nNote, mapping.extras ?: "", config)
        return mapping.copy(remoteNoteId = updatedNote.id, extras = updatedNote.etag)
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean = try {
        // Delete the note on the server
        if (BuildConfig.DEBUG) Log.d(tag, "deleteNote() called with: mapping = $mapping")
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        val api = apiProvider.getAPI()
        api.deleteNote(mapping.remoteNoteId, config)
        true
    } catch (_: Exception) {
        false
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        requireNotNull(mapping.remoteNoteId) { "Remote note id is null." }
        val api = apiProvider.getAPI()
        return api.getNote(mapping.remoteNoteId, config).asSyncNote()
    }

    override suspend fun getAll(): List<SyncNote>? = try {
        if (BuildConfig.DEBUG) Log.d(tag, "getAll() from Nextcloud")
        val api = apiProvider.getAPI()
        api.getNotes(config).map { note -> note.asSyncNote() }
    } catch (e: Exception) {
        Log.e(tag, "getAll: Error getting notes from Nextcloud", e)
        null
    }
}
