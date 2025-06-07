package org.qosp.notes.data.sync.neu

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.preferences.CloudService
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.measureTimedValue

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
            val titleToSet = title.ifBlank { "Untitled" }
            val ext = if (isMarkdownEnabled) "md" else "txt"
            return "${titleToSet.trim()}.$ext"
        }

    override suspend fun createNote(note: Note): NewSyncNote {
        val root = getRootDocumentFile() ?: throw IOException("Unable to access storage location")

        Log.d(TAG, "createNote: $note")
        val mimeType = if (note.isMarkdownEnabled) "text/markdown" else "text/plain"
        val newDoc = root.createFile(mimeType, note.filename)

        return newDoc?.let {
            writeNoteToFile(it, note.content)
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
        val uri = mapping.storageUri?.toUri() ?: throw IllegalArgumentException("URI cannot be null")
        val rootDoc = getRootDocumentFile() ?: throw IOException("Unable to access storage location")
        val file = DocumentFile.fromSingleUri(context, uri) ?: throw FileNotFoundException("URI not found")

        writeNoteToFile(file, note.content)
        val newUri = if (note.filename != file.name) renameFile(file, note.filename, rootDoc) else uri
        return mapping.copy(storageUri = newUri.toString())
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean {
        val uri = mapping.storageUri?.toUri() ?: return false
        val deletionResult = inStorage {
            val result = DocumentsContract.deleteDocument(context.contentResolver, uri)
            Log.d(TAG, "deleteNote: Deleted (${result}) the file: ${uri.pathSegments.last()}")
            result
        }
        return deletionResult == true
    }

    override suspend fun getNote(mapping: IdMapping): NewSyncNote? {
        return try {
            val uri = mapping.storageUri?.toUri() ?: return null
            val file = DocumentFile.fromSingleUri(context, uri) ?: return null
            getFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "getNote: Error getting note with id ${mapping.localNoteId}", e)
            null
        }
    }

    private fun getFile(file: DocumentFile) = NewSyncNote(
        id = 0,
        idStr = file.uri.toString(),
        content = readFileContent(file),
        title = getTitleFromUri(file.uri),
        lastModified = file.lastModified() / 1000, // Milliseconds to seconds
    )

    override suspend fun getAll(): List<NewSyncNote> {
        val root = getRootDocumentFile() ?: return emptyList()
        return try {
            val files = root.listFiles()
                .flatMap { if (it.isDirectory) it.listFiles().toList() else listOf(it) }
                .filter { it.name?.endsWith(".md") == true || it.name?.endsWith(".txt") == true }
            files.map { file -> getFile(file) }
        } catch (e: Exception) {
            Log.e(TAG, "getAll: Error listing files", e)
            emptyList()
        }
    }

    suspend fun validateConfig(): BackendValidationResult {
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

    private fun renameFile(file: DocumentFile, newName: String, root: DocumentFile): String {
        Log.d(TAG, "renameFile: Renaming ${file.name} to $newName")
        val foundFile = root.listFiles().firstOrNull { it.name == file.name }
            ?: throw FileNotFoundException("File ${file.name} not found")
        val succeeded = foundFile.renameTo(newName)
        Log.d(TAG, "renameFile: Renaming ${foundFile.name}, succeeded? $succeeded")
        return foundFile.uri.toString()
    }

    private inline fun <reified T> inStorage(block: () -> T): T? {
        return try {
            val duration = measureTimedValue {
                block()
            }
            Log.i(TAG, "inStorage: That took ${duration.duration} to complete")
            duration.value
        } catch (e: Exception) {
            Log.e(TAG, "Exception while storing: ${e.message}", e)
            null
        }
    }
}
