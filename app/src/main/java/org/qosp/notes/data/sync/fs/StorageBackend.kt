package org.qosp.notes.data.sync.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.firstOrNull
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.BackendValidationResult
import org.qosp.notes.preferences.CloudService
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.measureTimedValue

private const val MAX_NOTES_DIR_DEPTH = 10

class StorageBackend(
    private val context: Context,
    private val config: StorageConfig,
    private val notebookRepository: NotebookRepository
) : ISyncBackend {

    companion object {
        private const val TAG = "StorageBackend"
    }

    override val type: CloudService = CloudService.FILE_STORAGE

    private val Note.filename: String
        get() {
            val titleToSet = title.ifBlank { "Untitled" }
            val ext = if (isMarkdownEnabled) "md" else "txt"
            return "${titleToSet.trim()}.$ext"
        }

    private suspend fun getNoteLocation(note: Note): String {
        if (note.notebookId == null) return ""
        val notebook = notebookRepository.getById(note.notebookId).firstOrNull()
        return notebook?.name ?: ""
    }

    /**
     * Finds a nested directory from a base DocumentFile, creating it if it doesn't exist.
     *
     * @param basePath The starting directory (a DocumentFile).
     * @param relativePath A string representing the path, e.g., "Folder1/FolderA/Final Folder".
     * @return The DocumentFile for the final directory in the path, or null if creation fails or a file is found instead of a directory.
     */
    private fun findOrCreateDirectoryFromPath(basePath: DocumentFile, relativePath: String): DocumentFile? {
        // If the path is empty, return the base path directly.
        if (relativePath.isBlank()) {
            return basePath
        }

        // Split the path into folder segments, filtering out any empty parts (e.g., from double slashes).
        val folders = relativePath.split('/').filter { it.isNotBlank() }
        var currentDirectory = basePath

        // Iterate over each folder name in the path.
        for (folderName in folders) {
            // Search for the subdirectory in the current directory.
            val nextDirectory = currentDirectory.findFile(folderName)

            currentDirectory = if (nextDirectory != null) {
                // If a file with this name exists but is not a directory, it's an error.
                if (!nextDirectory.isDirectory) {
                    Log.e(TAG, "A file named '$folderName' exists at this location and is not a directory.")
                    return null
                }
                nextDirectory
            } else {
                // If the directory does not exist, create it.
                // createDirectory() returns null on failure.
                currentDirectory.createDirectory(folderName) ?: run {
                    Log.e(TAG, "Failed to create directory: '$folderName'")
                    return null
                }
            }
        }
        // Return the last directory found or created.
        return currentDirectory
    }


    override suspend fun createNote(note: Note): SyncNote {
        Log.d(TAG, "createNote: $note")
        val noteDocumentFile = getNoteDocumentFile(note)
        val mimeType = if (note.isMarkdownEnabled) "text/markdown" else "text/plain"
        val newDoc = noteDocumentFile.createFile(mimeType, note.filename)

        return newDoc?.let {
            writeNoteToFile(it, note.toStorableContent())
            SyncNote(
                idStr = newDoc.uri.toString(),
                title = note.title,
                content = note.toStorableContent(),
                lastModified = newDoc.lastModified() / 1000, // Epoch milliseconds to seconds
                id = 0
            )
        } ?: throw IOException("Unable to create file for ${note.filename}")
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        val uri = mapping.storageUri?.toUri() ?: throw IllegalArgumentException("URI cannot be null")
        val rootDir = getRootDocumentFile() ?: throw IOException("Unable to access storage location")
        val file = findFileByUri(rootDir, uri) ?: throw FileNotFoundException("File with URI $uri not found in storage tree")

        // By finding the file via tree traversal, parentFile is now reliable.
        val parentDir = file.parentFile ?: throw IOException("Could not determine parent directory for ${file.name}")

        val noteDocFile = getNoteDocumentFile(note)

        writeNoteToFile(file, content = note.toStorableContent())

        val newUriAfterRename = if (note.filename != file.name) {
            renameFile(file, note.filename)
        } else {
            uri.toString()
        }

        val finalUri = if (parentDir.uri != noteDocFile.uri) {
            Log.d(TAG, "Notebook changed, moving file from '${parentDir.name}' to '${noteDocFile.name}'")
            val movedUri = inStorage {
                DocumentsContract.moveDocument(
                    context.contentResolver,
                    newUriAfterRename.toUri(),
                    parentDir.uri,
                    noteDocFile.uri
                )
            }

            if (movedUri != null) {
                Log.d(TAG, "File moved successfully. New URI: $movedUri")
                movedUri.toString()
            } else {
                Log.e(TAG, "Failed to move file to new notebook directory.")
                newUriAfterRename // fallback to uri before move
            }
        } else {
            newUriAfterRename
        }

        return mapping.copy(storageUri = finalUri)
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

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        return try {
            val uri = mapping.storageUri?.toUri() ?: return null
            val file = DocumentFile.fromSingleUri(context, uri) ?: return null
            getFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "getNote: Error getting note with id ${mapping.localNoteId}", e)
            null
        }
    }

    private fun getFile(file: DocumentFile): SyncNote {
        val rootDoc = getRootDocumentFile() ?: throw IOException("Unable to access storage location")
        return SyncNote(
            id = 0,
            idStr = file.uri.toString(),
            content = readFileContent(file),
            title = getTitleFromUri(file.uri),
            lastModified = file.lastModified() / 1000, // Milliseconds to seconds,
            category = getRelativePath(rootDoc, file)
        )
    }

    private fun findNoteFilesRecursively(directory: DocumentFile, depth: Int = 0): List<DocumentFile> {
        if (depth > MAX_NOTES_DIR_DEPTH) {
            Log.w(TAG, "Max depth reached, returning empty list")
            return emptyList()
        }
        Log.d(TAG, "Scanning directory: ${directory.name}")
        val result = mutableListOf<DocumentFile>()

        for (file in directory.listFiles()) {
            if (file.isDirectory) {
                if (file.name?.startsWith('.') == true) {
                    Log.d(TAG, "Skipping hidden directory: ${file.name}")
                    continue // Skip hidden directories
                }
                result.addAll(findNoteFilesRecursively(file, depth + 1))
            } else {
                val fileName = file.name ?: ""
                if (fileName.startsWith('.') || (!(fileName.endsWith(".md") && !fileName.endsWith(".txt")))) {
                    Log.d(TAG, "Skipping file: $fileName")
                    continue // Skip hidden files and non-markdown or text files
                }
                Log.d(TAG, "Found note file: $fileName")
                result.add(file)
            }
        }
        return result
    }

    override suspend fun getAll(): List<SyncNote>? {
        val root = getRootDocumentFile() ?: return null
        return try {
            findNoteFilesRecursively(root).map { getFile(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getAll: Error listing files", e)
            null
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

    private suspend fun getNoteDocumentFile(note: Note): DocumentFile {
        val root = getRootDocumentFile() ?: throw IOException("Unable to access storage location")
        val noteLocation = getNoteLocation(note)
        Log.d(TAG, "Note location: $noteLocation")
        val noteDir = findOrCreateDirectoryFromPath(root, noteLocation) ?: root
        return noteDir
    }

    /**
     * Calculates the relative path from a root directory to a given file's parent directory.
     *
     * For example, if root is "/" and file is "/notes/Folder1/note.md",
     * this function will return "/notes/Folder1". If the file is directly inside root, it returns an empty string.
     *
     * @param root The base DocumentFile directory.
     * @param file The DocumentFile for which to find the relative parent path.
     * @return A string representing the relative path (e.g., "Folder1/Folder2"), or an empty string if the
     *         file is in the root or not a descendant of the root.
     */
    private fun getRelativePath(root: DocumentFile, file: DocumentFile): String {
        // Find the direct parent of the file. If it doesn't exist, we can't proceed.
        val parent = file.parentFile ?: return ""

        // If the parent is the root directory, there is no relative path.
        if (parent.uri == root.uri) {
            return ""
        }

        val pathSegments = mutableListOf<String>()
        var currentParent: DocumentFile? = parent

        // Traverse up from the file's parent until we reach the root directory.
        // The loop is protected against running indefinitely, stopping after 20 levels.
        for (i in 0..MAX_NOTES_DIR_DEPTH) {
            if (currentParent == null || currentParent.uri == root.uri) {
                break // Stop if we've reached the root or gone too far
            }

            // Add the directory name to the beginning of our list
            currentParent.name?.let { pathSegments.add(0, it) }

            // Move to the next parent up
            currentParent = currentParent.parentFile
        }

        // Join the path segments with slashes to form the final relative path string.
        return pathSegments.joinToString("/")
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

    private fun renameFile(file: DocumentFile, newName: String): String {
        val oldUriString = file.uri.toString()
        Log.d(TAG, "renameFile: Renaming ${file.name} to $newName")
        return if (file.renameTo(newName)) {
            Log.d(TAG, "renameFile: Renaming to $newName succeeded. New URI is ${file.uri}")
            file.uri.toString()
        } else {
            Log.e(TAG, "renameFile: Renaming to $newName failed.")
            oldUriString // Return original URI if failed
        }
    }

    private fun findFileByUri(root: DocumentFile, targetUri: Uri): DocumentFile? {
        // Extract the document ID from the URI
        val rootDocId = DocumentsContract.getDocumentId(root.uri)
        val targetDocId = DocumentsContract.getDocumentId(targetUri)

        // Check if the target URI is under the root
        if (!targetDocId.startsWith(rootDocId)) {
            return null
        }

        // Remove the root document ID to get the relative path
        val relativePath = targetDocId.removePrefix("$rootDocId/")
        if (relativePath.isEmpty() || relativePath == rootDocId) {
            return root // It's the root itself
        }

        // Split the relative path into segments and traverse the tree
        val segments = relativePath.split("/")
        var current: DocumentFile? = root

        for (segment in segments) {
            current = current?.listFiles()?.find { it.name == segment }
            if (current == null) return null
        }

        return current
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
