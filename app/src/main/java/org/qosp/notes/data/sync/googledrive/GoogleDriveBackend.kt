package org.qosp.notes.data.sync.googledrive

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.preferences.CloudService

/**
 * Google Drive sync backend implementation
 * Uses Google Drive API to sync notes with Google Drive
 */
class GoogleDriveBackend(
    private val config: GoogleDriveConfig
) : ISyncBackend {
    override val type: CloudService = CloudService.GOOGLE_DRIVE

    // TODO: Implement Google Drive sync operations using Google Drive API
    // Notes will be stored as Markdown files in a dedicated folder

    override suspend fun createNote(note: Note): SyncNote {
        // TODO: Create note as .md file in Google Drive
        throw NotImplementedError("Google Drive sync is not yet fully implemented")
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        // TODO: Update existing note file in Google Drive
        throw NotImplementedError("Google Drive sync is not yet fully implemented")
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean {
        // TODO: Delete note file from Google Drive
        throw NotImplementedError("Google Drive sync is not yet fully implemented")
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        // TODO: Get note from Google Drive by ID
        throw NotImplementedError("Google Drive sync is not yet fully implemented")
    }

    override suspend fun getAll(): List<SyncNote>? {
        // TODO: List all notes from Google Drive folder
        throw NotImplementedError("Google Drive sync is not yet fully implemented")
    }
}
