package org.qosp.notes.components.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.qosp.notes.App
import org.qosp.notes.data.Backup
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.model.Tag
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.ui.attachments.getAttachmentUri
import org.qosp.notes.ui.reminders.ReminderManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val currentVersion: Int,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val tagRepository: TagRepository,
    private val reminderRepository: ReminderRepository,
    private val idMappingRepository: IdMappingRepository,
    private val reminderManager: ReminderManager,
    private val context: Context,
) {
    private val BUFFER = 2048

    /**
     * Creates a backup which contains [notes] or the whole database if [notes] is null.
     */
    suspend fun createBackup(
        notes: Set<Note>?,
        attachmentHandler: AttachmentHandler,
    ): Backup {
        val notes = notes ?: noteRepository
            .getAll()
            .first()
            .toSet()

        val notebooks = mutableSetOf<Notebook>()
        val reminders = mutableSetOf<Reminder>()
        val tags = mutableSetOf<Tag>()
        val joins = mutableSetOf<NoteTagJoin>()
        val idMappings = mutableSetOf<IdMapping>()

        val newNotes = notes.map { note ->
            note.notebookId?.let { notebookId ->
                val notebook = notebookRepository.getById(notebookId).first() ?: return@let
                notebooks.add(notebook)
            }

            val noteReminders = reminderRepository.getByNoteId(note.id).first()
            reminders.addAll(noteReminders)

            val noteTags = tagRepository.getByNoteId(note.id).first()
            tags.addAll(noteTags)

            val noteTagJoins = noteTags.map { tag -> NoteTagJoin(tag.id, note.id) }
            joins.addAll(noteTagJoins)

            val mappings = idMappingRepository.getAllByLocalId(note.id)
            idMappings.addAll(mappings)

            val newAttachments = mutableListOf<Attachment>()
            note.attachments.forEach { old ->
                attachmentHandler
                    .handle(old)
                    ?.let { newAttachments.add(it) }
            }
            note.copy(attachments = newAttachments)
        }

        return Backup(currentVersion, newNotes.toSet(), notebooks, reminders, tags, joins, idMappings)
    }

    suspend fun restoreNotesFromBackup(backup: Backup) {
        val notebooksMap = mutableMapOf<Long, Long>()
        val tagsMap = mutableMapOf<Long, Long>()
        val notesMap = mutableMapOf<Long, Long>()

        backup.notebooks.forEach { notebook ->
            val existingNotebook = notebookRepository.getByName(notebook.name).firstOrNull()
            if (existingNotebook != null) {
                notebooksMap[notebook.id] = existingNotebook.id
            } else {
                notebooksMap[notebook.id] = notebookRepository.insert(notebook.copy(id = 0L))
            }
        }

        backup.tags.forEach { tag ->
            val existingTag = tagRepository.getByName(tag.name).firstOrNull()
            if (existingTag != null) {
                tagsMap[tag.id] = existingTag.id
                return@forEach
            }
            tagsMap[tag.id] = tagRepository.insert(tag.copy(id = 0L))
        }

        backup.notes.forEach { note ->
            val newNote = note.copy(
                id = 0L,
                notebookId = notebooksMap[note.notebookId],
                attachments = note.attachments.map { attachment ->
                    if (attachment.fileName.isNotEmpty()) {
                        attachment.copy(
                            path = getAttachmentUri(context, attachment.fileName).toString(),
                            fileName = ""
                        )
                    } else {
                        attachment
                    }
                }
            )
            notesMap[note.id] = noteRepository.insertNote(newNote)
        }

        backup.idMappings.forEach {
            val noteId = notesMap[it.localNoteId] ?: return@forEach
            val newMapping = it.copy(mappingId = 0L, localNoteId = noteId)

            idMappingRepository.assignProviderToNote(newMapping)
        }

        backup.joins.forEach { join ->
            val tagId = tagsMap[join.tagId] ?: return@forEach
            val noteId = notesMap[join.noteId] ?: return@forEach
            tagRepository.addTagToNote(tagId, noteId)
        }

        backup.reminders.forEach { reminder ->
            if (reminder.hasExpired()) return@forEach

            val noteId = notesMap[reminder.noteId] ?: return@forEach
            val reminderId = reminderRepository.insert(reminder.copy(id = 0L, noteId = noteId))
            reminderManager.schedule(
                reminderId = reminderId,
                noteId = noteId,
                dateTime = reminder.date
            )
        }
    }

    fun backupFromZipFile(
        uri: Uri,
        migrationHandler: MigrationHandler,
    ): Result<Backup> = runCatching {
        var backup: Backup? = null
        val nameMap = mutableMapOf<String, String>()

        ZipInputStream(BufferedInputStream(context.contentResolver.openInputStream(uri))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                when (entry.name) {
                    "backup.json" -> {
                        // Create backup class
                        val builder = StringBuilder()
                        val buffer = ByteArray(BUFFER)
                        var length = 0
                        while (input.read(buffer).also { length = it } > 0) {
                            builder.append(String(buffer, 0, length))
                        }
                        val deserialized = migrationHandler.migrate(builder.toString())

                        backup = Backup.fromString(deserialized)
                    }

                    "${App.MEDIA_FOLDER}/" -> {
                        // Ignore directory
                        continue
                    }

                    // Copy media files to local storage
                    else -> {
                        val dir = File(context.filesDir, App.MEDIA_FOLDER).also { it.mkdirs() }

                        var fileId = 1
                        val originalName = entry.name.split("/").last()
                        val generateFilename = { "${fileId}_$originalName".also { fileId += 1 } }
                        var fileName = originalName

                        // If a file with the same name already exists in app storage
                        // give the new file a new name and loop until it is unique
                        do {
                            val exists = dir.listFiles()?.any { it.name == fileName } == true
                            if (exists) {
                                fileName = generateFilename()
                                // Map the old name to the new name so we can change the notes later to use the new name
                                nameMap[originalName] = fileName
                            }
                        } while (exists)

                        FileOutputStream(File(dir, fileName)).use { out ->
                            val buffer = ByteArray(BUFFER)
                            var length = 0
                            while (input.read(buffer).also { length = it } > 0) {
                                out.write(buffer, 0, length)
                            }
                        }
                        input.closeEntry()
                    }
                }
            }
        }

        backup.run {
            if (this == null || nameMap.isEmpty()) return@run this
            val newNotes = notes
                .map { note ->
                    val newAttachments = note.attachments.map { attachment ->
                        attachment.copy(fileName = nameMap[attachment.fileName] ?: attachment.fileName)
                    }
                    note.copy(attachments = newAttachments)
                }
                .toSet()

            copy(notes = newNotes)
        } ?: throw IOException()
    }

    fun createBackupZipFile(
        noteJson: String,
        handler: AttachmentHandler,
        uri: Uri,
        progressHandler: ProgressHandler,
    ) {
        runCatching {
            ZipOutputStream(BufferedOutputStream(context.contentResolver.openOutputStream(uri))).use { out ->
                var current = 0
                var max = 1

                if (handler is AttachmentHandler.IncludeFiles) {
                    val attachments = handler.attachmentsMap
                    max = attachments.size + 1

                    if (attachments.isNotEmpty()) out.putNextEntry(ZipEntry("${App.MEDIA_FOLDER}/"))
                    for ((fileName, inputUri) in attachments) {
                        progressHandler.onProgressChanged(++current, max)

                        out.putNextEntry(ZipEntry("${App.MEDIA_FOLDER}/$fileName"))
                        context.contentResolver.openInputStream(inputUri)?.use { input ->
                            input.copyTo(out, BUFFER)
                        }
                    }
                }

                progressHandler.onProgressChanged(++current, max)
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(noteJson.toByteArray())
                out.finish()
            }
        }.fold(
            onSuccess = { progressHandler.onCompletion() },
            onFailure = { progressHandler.onFailure(it) }
        )
    }
}
