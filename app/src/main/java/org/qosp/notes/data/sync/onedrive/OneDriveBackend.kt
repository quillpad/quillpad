package org.qosp.notes.data.sync.onedrive

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.preferences.CloudService

/**
 * OneDrive sync backend implementation
 * Uses Microsoft Graph API to sync notes with OneDrive
 */
class OneDriveBackend(
    private val config: OneDriveConfig
) : ISyncBackend {
    override val type: CloudService = CloudService.ONEDRIVE

    // TODO: Implement OneDrive sync operations using Microsoft Graph SDK
    // Notes will be stored as Markdown files in a dedicated folder

    override suspend fun createNote(note: Note): SyncNote {
        // TODO: Create note as .md file in OneDrive
        throw NotImplementedError("OneDrive sync is not yet fully implemented")
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        // TODO: Update existing note file in OneDrive
        throw NotImplementedError("OneDrive sync is not yet fully implemented")
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean {
        // TODO: Delete note file from OneDrive
        throw NotImplementedError("OneDrive sync is not yet fully implemented")
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        // TODO: Get note from OneDrive by ID
        throw NotImplementedError("OneDrive sync is not yet fully implemented")
    }

    override suspend fun getAll(): List<SyncNote>? {
        // TODO: List all notes from OneDrive folder
        throw NotImplementedError("OneDrive sync is not yet fully implemented")
    }
}
