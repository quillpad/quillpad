package org.qosp.notes.data.sync.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.NoteFile
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.core.SyncResult
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.CloudService.FILE_STORAGE
import org.qosp.notes.preferences.PreferenceRepository
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class StorageBackend(
    private val prefRepo: PreferenceRepository,
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val idMappingRepository: IdMappingRepository
) : ISyncBackend<StorageConfig, NoteFile> {

    companion object {
        private const val TAG = "StorageManager"
    }

    private val Note.filename: String
        get() {
            val ext = if (isMarkdownEnabled) "md" else "txt"
            return "${title.trim()}__$id.$ext"
        }

    override suspend fun getSyncNoteFrom(note: Note, idMapping: IdMapping): NoteFile {
        return NoteFile(note.modifiedDate, note.content, note.title, idMapping.storageUri?.let { Uri.parse(it) })
    }

//    @OptIn(ExperimentalTime::class)
//    suspend fun sync(config: StorageConfig) = inStorage(config) { root, sConfig ->
//        Log.i(TAG, "sync: With config $config")
//
//        if (!root.uri.toString().startsWith(sConfig.location.toString())) {
//            // Delete all id Mappings and create new id mappings
//            val dbDeleteTime = measureTime {
//                val mappings = idMappingRepository.getAllByProvider(FILE_STORAGE)
//                idMappingRepository.delete(*mappings.toTypedArray())
//            }
//            Log.i(TAG, "sync: dbDeleteTime: $dbDeleteTime")
//        }
//
//        val dirContentsTime = measureTimedValue {
//            root.listFiles()
//                .flatMap { if (it.isDirectory) it.listFiles().toList() else listOf(it) }
//                .associateBy { it.uri.toString() }
//        }
//        Log.i(TAG, "sync: readDirContents: ${dirContentsTime.duration}")
//        val dirContents = dirContentsTime.value
//        // Phase 1.
//        // File system -> Quillpad notes sync
//        val allStorageNotes = noteRepository.getNotesByCloudService(FILE_STORAGE)
//        // Import new files to quillpad
//        dirContents.filterKeys { it !in allStorageNotes.keys.mapNotNull { m -> m.storageUri } }
//            .mapValues { (url, file) -> importFileAtUrl(file, url) }.filter { it.value == null }.forEach {
//                Log.i(TAG, "sync: Couldn't import ${it.key}")
//            }
//
//        // delete remotely deleted notes
//        allStorageNotes.filterKeys { it.storageUri !in dirContents.keys }.forEach { (mapping, note) ->
//            Log.d(TAG, "sync: Deleted remotely `${note?.title}`")
//            idMappingRepository.delete(mapping)
//            note?.let { noteRepository.deleteNotes(it, shouldSync = false) }
//        }
//
//        // update local notes with new content from storage
//        dirContents.forEach { (url, file) ->
//            allStorageNotes.filter { (mapping, note) ->
//                note != null
//                    && mapping.storageUri == url
//                    && note.modifiedDate < file.lastModified()
//            }.forEach { (_, note) ->
//                note?.let {
//                    readFileContent(file)?.let { content ->
//                        Log.i(TAG, "sync: Newer files is updating local note `${note.title}`")
//                        val newNote = note.copy(modifiedDate = file.lastModified(), content = content)
//                        noteRepository.updateNotes(newNote, shouldSync = false)
//                    }
//                }
//            }
//        }
//
//        // Phase 2.
//        // Quillpad changes -> file system sync
//
//        // locally created notes that are not mapped to any remote id
//        val newLocalNotes = noteRepository.getNonRemoteNotes(FILE_STORAGE).first()
//        newLocalNotes.forEach { createNote(it, config) }
//
//        // *For Locally deleted or missing*
//        // remove mappings, Delete file if it exists
//        val locallyDeleted = allStorageNotes
//            .filterValues { it == null || it.isDeleted || it.isLocalOnly }
//            .mapValues { (mapping, _) -> dirContents[mapping.storageUri] }
//        idMappingRepository.delete(*locallyDeleted.keys.toTypedArray())
//        locallyDeleted.values.filterNotNull().forEach { it.delete() }
//
//        // TODO this is not done. Cannot differentiate from delete files vs ref to old location
//        // Remapped storage location:
//        // This mapping is invalid coz the file doesn't exist. It's mapped to old location.
//        // Delete the mapping and create new.
//        //        allStorageNotes.filterKeys { it.storageUri !in filesAndUrls.keys }.forEach { (m, note) ->
//        //            idMappingRepository.delete(m)
//        //            note?.let { createNote(it, config) }
//        //        }
//
//        allStorageNotes.mapNotNull { (m, n) -> n?.let { Pair(m, n) } }
//            .forEach { (m, note) ->
//                val file = dirContents[m.storageUri] ?: return@forEach
//                // Renamed files (File with IDs as name instead of title)
//                if (file.name != note.filename) {
//                    renameFile(file, note.filename, root, m)
//                }
//            }
//    }

    private suspend fun importFileAtUrl(file: DocumentFile, url: String): Long? {
        val (title, isMarkdown) = file.name?.let {
            when {
                it.endsWith(".md") -> Pair(it.removeSuffix(".md"), true)
                it.endsWith(".txt") -> Pair(it.removeSuffix(".txt"), false)
                else -> return null
            }
        } ?: return null
        val content = readFileContent(file) ?: return null
        val noteId = noteRepository.insertNote(
            Note(
                title = title,
                content = content,
                modifiedDate = file.lastModified(),
                isMarkdownEnabled = isMarkdown
            ),
            shouldSync = false
        )
        idMappingRepository.insert(
            IdMapping(
                localNoteId = noteId,
                storageUri = url,
                provider = FILE_STORAGE,
                extras = null,
                isDeletedLocally = false,
                remoteNoteId = null
            )
        )
        return noteId
    }

    override suspend fun createNote(note: Note, config: StorageConfig) = inStorage(config) { root, _ ->
        Log.d(TAG, "createNote: $note")
        val mimeType = if (note.isMarkdownEnabled) "text/markdown" else "text/plain"
        val newDoc = root.createFile(mimeType, note.filename)
        newDoc?.let {
            writeNoteToFile(newDoc, note.content)
            NoteFile(note.modifiedDate, note.content, note.title, newDoc.uri)
        } ?: throw IOException("Unable to create file for ${note.filename}")
    }

    override suspend fun deleteNote(note: NoteFile, config: StorageConfig) =
        inStorage(config) { _, _ ->
            note.uri?.let {
                if (DocumentsContract.deleteDocument(context.contentResolver, it)) {
                    Log.i(TAG, "deleteNote: Deleted the file ${note.uri.pathSegments.last()}")
                } else {
                    Log.i(TAG, "deleteNote: Unable to delete ${note.uri.pathSegments.last()}")
                }
            }
            Unit
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun list(config: StorageConfig): SyncResult<List<SyncNote>> = inStorage(config) { root, _ ->
        val dirContentsTime = measureTimedValue {
            root.listFiles()
                .flatMap { if (it.isDirectory) it.listFiles().toList() else listOf(it) }
                .toList()
        }
        Log.i(TAG, "readDirContents: ${dirContentsTime.duration}")
        val dirContents = dirContentsTime.value
        dirContents.map { NoteFile(it.lastModified(), null, getTitleFromUri(it.uri), it.uri) }
    }

    private fun getTitleFromUri(uri: Uri): String {
        TODO("Not yet implemented")
    }

    override suspend fun updateNote(note: NoteFile, config: StorageConfig) = inStorage(config) { _, _ ->
        note.uri?.let {
            val file = DocumentFile.fromSingleUri(context, it) ?: throw FileNotFoundException("URI not found")
            writeNoteToFile(file, note.content ?: "")
        }
        Unit
    }

    override suspend fun getNoteContent(note: NoteFile, config: StorageConfig) = inStorage(config) { _, _ ->
        val uri = note.uri ?: throw IllegalArgumentException("URI cannot be null")
        val file = DocumentFile.fromSingleUri(context, uri) ?: throw FileNotFoundException("URI not found")
        NoteFile(file.lastModified(), readFileContent(file),getTitleFromUri(uri), uri)
    }

    private suspend fun renameFile(file: DocumentFile, filename: String, root: DocumentFile, mapping: IdMapping) {
        Log.d(TAG, "renameFile: Renaming ${file.name} to $filename")
        root.listFiles().firstOrNull { it.name == file.name }?.let {
            val succeeded = it.renameTo(filename)
            Log.d(TAG, "renameFile: Renaming ${it.name}, succeeded? $succeeded")
            if (succeeded) {
                idMappingRepository.update(mapping.copy(storageUri = it.uri.toString()))
            }
        } ?: run { Log.d(TAG, "renameFile: File ${file.name} not found") }
    }

    private fun readFileContent(file: DocumentFile): String? {
        Log.d(TAG, "readFileContent: ${file.name}")
        return context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
    }

    @OptIn(ExperimentalTime::class)
    private inline fun <T> inStorage(config: StorageConfig, block: (DocumentFile, StorageConfig) -> T): SyncResult<T> {
        val root = DocumentFile.fromTreeUri(config.context, config.location) ?: return SyncResult.Error(
            FileNotFoundException("Unable to find ${config.location}")
        )
        if (!hasPermissionsAt(config.location))
            return SyncResult.Error(IllegalAccessError("No permissions at ${config.location}"))
        return try {
            val result = measureTimedValue { block(root, config) }
            Log.i(TAG, "inStorage: That took ${result.duration} to complete")
            SyncResult.Success(result.value)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while storing: ${e.message}", e)
            SyncResult.Error(e)
        }
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

    private fun hasPermissionsAt(uri: Uri): Boolean {
        val perm = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return perm?.let { it.isReadPermission && it.isWritePermission } ?: false
    }

    override val service: CloudService = FILE_STORAGE

    override suspend fun isServerCompatible(config: StorageConfig) = Success

    override suspend fun authenticate(config: StorageConfig) = Success
}
