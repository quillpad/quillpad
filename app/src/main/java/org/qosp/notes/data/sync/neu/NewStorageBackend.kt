package org.qosp.notes.data.sync.neu

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.NoteFile
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.preferences.CloudService
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class NewStorageBackend(
    private val context: Context,
    private val config: StorageConfig
) : INewSyncBackend {

    companion object {
        private const val TAG = "NewStorageBackend"
    }

    override val type: CloudService = CloudService.FILE_STORAGE

    private val Note.filename: String
        get() {
            val ext = if (isMarkdownEnabled) "md" else "txt"
            return "${title.trim()}.$ext"
        }

    override suspend fun createNote(note: Note): NewSyncNote {
        val root = getRootDocumentFile() ?: throw IOException("Unable to access storage location")

        Log.d(TAG, "createNote: $note")
        val mimeType = if (note.isMarkdownEnabled) "text/markdown" else "text/plain"
        val newDoc = root.createFile(mimeType, note.filename)

        return newDoc?.let {
            writeNoteToFile(it, note.content)
            NoteFile(note.modifiedDate, note.content, note.title, newDoc.uri)
            NewSyncNote(
                idStr = newDoc.uri.toString(),
                title = note.title,
                content = note.content,
                lastModified = newDoc.lastModified(),
                id = 0
            )
        } ?: throw IOException("Unable to create file for ${note.filename}")
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        return mapping.storageUri?.toUri()?.let { uri ->
            val file = DocumentFile.fromSingleUri(context, uri) ?: throw FileNotFoundException("URI not found")
            writeNoteToFile(file, note.content)
            val newUri = if (note.filename != file.name) {
                val succeeded = file.renameTo(note.filename)
                if (!succeeded) {
                    throw IOException("Unable to rename file to ${file.name}")
                }
                file.uri
            } else uri
            mapping.copy(storageUri = newUri.toString())
        } ?: throw IllegalArgumentException("URI cannot be null")
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean {
        return mapping.storageUri?.toUri()?.let { uri ->
            if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                Log.d(TAG, "deleteNote: Deleted the file ${uri.pathSegments.last()}")
                true
            } else {
                Log.i(TAG, "deleteNote: Unable to delete ${uri.pathSegments.last()}")
                false
            }
        } ?: false
    }

    override suspend fun getNote(mapping: IdMapping): NewSyncNote? {
        return try {
            val uri = mapping.storageUri?.toUri() ?: return null
            val file = DocumentFile.fromSingleUri(context, uri) ?: return null
            NewSyncNote(
                lastModified = file.lastModified(),
                content = readFileContent(file),
                title = getTitleFromUri(uri),
                idStr = uri.toString(),
                id = 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "getNote: Error getting note with id ${mapping.localNoteId}", e)
            null
        }
    }

    override suspend fun getAll(): List<NewSyncNote> {
        val root = getRootDocumentFile() ?: return emptyList()

        return try {
            val files = root.listFiles()
                .flatMap { if (it.isDirectory) it.listFiles().toList() else listOf(it) }
                .filter { it.name?.endsWith(".md") == true || it.name?.endsWith(".txt") == true }

            files.map { file ->
                NewSyncNote(
                    idStr = file.uri.toString(),
                    title = getTitleFromUri(file.uri),
                    lastModified = file.lastModified(),
                    content = "",
                    id = 0,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAll: Error listing files", e)
            emptyList()
        }
    }

    override suspend fun validateConfig(): BackendValidationResult {
        return try {
            val root = getRootDocumentFile()
            if (root == null || !hasPermissionsAt(config.location)) {
                BackendValidationResult.InvalidConfig
            } else {
                BackendValidationResult.Success
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateConfig: Error validating config", e)
            BackendValidationResult.InvalidConfig
        }
    }

    private fun getRootDocumentFile(): DocumentFile? {
        return DocumentFile.fromTreeUri(context, config.location)
    }

    private fun hasPermissionsAt(uri: Uri): Boolean {
        val perm = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return perm?.let { it.isReadPermission && it.isWritePermission } ?: false
    }

    private fun writeNoteToFile(file: DocumentFile, content: String) {
        context.contentResolver.openOutputStream(file.uri, "w")?.use { output ->
            (output as? FileOutputStream)?.let {
                output.channel.truncate(0)
                val bytesWritten = content.encodeToByteArray().inputStream().copyTo(output)
                Log.d(TAG, "writeNote: Wrote $bytesWritten bytes to ${file.name}")
            } ?: run {
                Log.e(TAG, "writeNoteToDocument: ${file.name} is not a file. URI:${file.uri}")
            }
        }
    }

    private fun readFileContent(file: DocumentFile): String? {
        Log.d(TAG, "readFileContent: ${file.name}")
        return context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
    }

    private fun getTitleFromUri(uri: Uri): String {
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        return when {
            fileName.endsWith(".md") -> fileName.removeSuffix(".md")
            fileName.endsWith(".txt") -> fileName.removeSuffix(".txt")
            else -> fileName
        }
    }
}
