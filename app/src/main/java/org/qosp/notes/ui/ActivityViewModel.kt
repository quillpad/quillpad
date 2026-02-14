package org.qosp.notes.ui

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msoul.datastore.defaultOf
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.GroupNotesWithoutNotebook
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.preferences.SortNavdrawerNotebooksMethod
import org.qosp.notes.preferences.SortTagsMethod
import org.qosp.notes.ui.reminders.ReminderManager
import org.qosp.notes.ui.utils.Toaster
import java.time.Instant

class ActivityViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val preferenceRepository: PreferenceRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderManager: ReminderManager,
    private val tagRepository: TagRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val syncScope: SyncScope,
    private val toaster: Toaster,
) : ViewModel() {

    val notebooks: StateFlow<Pair<Boolean, List<Notebook>>> =
        preferenceRepository.get<GroupNotesWithoutNotebook>().flatMapLatest { groupNotesWithoutNotebook ->
            notebookRepository.getAll().map { notebooks ->
                (groupNotesWithoutNotebook == GroupNotesWithoutNotebook.YES) to notebooks
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = (defaultOf<GroupNotesWithoutNotebook>() == GroupNotesWithoutNotebook.YES) to listOf(),
            )

    var showHiddenNotes: Boolean = false
    var notesToBackup: Set<Note>? = null
    var tempPhotoUri: Uri? = null

    fun syncAsync(): Deferred<BaseResult> = syncScope.async { noteRepository.syncNotes() }

    fun discardEmptyNotesAsync() = viewModelScope.async(Dispatchers.IO) { noteRepository.discardEmptyNotes() }

    fun deleteNotesPermanently(vararg notes: Note) = viewModelScope.launch(Dispatchers.IO) {
        notes.forEach { reminderManager.cancelAllRemindersForNote(it.id) }
        noteRepository.deleteNotes(*notes)
    }

    fun deleteNotes(vararg notes: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { reminderManager.cancelAllRemindersForNote(it.id) }

            when (preferenceRepository.get<NoteDeletionTime>().first()) {
                NoteDeletionTime.INSTANTLY -> {
                    noteRepository.deleteNotes(*notes)
                    mediaStorageManager.cleanUpStorage()
                }

                else -> {
                    noteRepository.moveNotesToBin(*notes)
                }
            }
        }
    }

    fun restoreNotes(vararg notes: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { noteRepository.restoreNotes(*notes) }
            if (result.isFailure) {
                toaster.showLong(result.exceptionOrNull()?.message ?: "Error restoring notes")
            }
        }
    }

    fun archiveNotes(vararg notes: Note) =
        update(*notes) { it.copy(isArchived = true, modifiedDate = Instant.now().epochSecond) }

    fun unarchiveNotes(vararg notes: Note) =
        update(*notes) { it.copy(isArchived = false, modifiedDate = Instant.now().epochSecond) }

    fun showNotes(vararg notes: Note) =
        update(*notes) { it.copy(isHidden = false, modifiedDate = Instant.now().epochSecond) }

    fun hideNotes(vararg notes: Note) =
        update(*notes) { it.copy(isHidden = true, modifiedDate = Instant.now().epochSecond) }

    fun pinNotes(vararg notes: Note) =
        update(*notes) { it.copy(isPinned = !it.isPinned, modifiedDate = Instant.now().epochSecond) }

    fun compactPreviewNotes(vararg notes: Note) = update(*notes) { it.copy(isCompactPreview = true) }

    fun fullPreviewNotes(vararg notes: Note) = update(*notes) { it.copy(isCompactPreview = false) }

    fun moveNotes(notebookId: Long?, vararg notes: Note) =
        update(*notes) { it.copy(notebookId = notebookId, modifiedDate = Instant.now().epochSecond) }

    fun makeNotesSyncable(vararg notes: Note) = update(*notes) { it.copy(isLocalOnly = false) }

    fun makeNotesLocal(vararg notes: Note) = update(*notes) { it.copy(isLocalOnly = true) }

    fun makeNotesFullPreview(vararg notes: Note) = update(*notes) { it.copy(isCompactPreview = false) }

    fun makeNotesCompactPreview(vararg notes: Note) = update(*notes) { it.copy(isCompactPreview = true) }

    fun disableScreenAlwaysOn(vararg notes: Note) = update(*notes) { it.copy(screenAlwaysOn = false) }

    fun enableScreenAlwaysOn(vararg notes: Note) = update(*notes) { it.copy(screenAlwaysOn = true) }

    fun disableMarkdown(vararg notes: Note) =
        update(*notes) { it.copy(isMarkdownEnabled = false, modifiedDate = Instant.now().epochSecond) }

    fun enableMarkdown(vararg notes: Note) =
        update(*notes) { it.copy(isMarkdownEnabled = true, modifiedDate = Instant.now().epochSecond) }

    fun duplicateNotes(vararg notes: Note) = notes.forEachAsync { note ->
        val oldId = note.id
        val cloned = note.copy(
            id = 0L,
            creationDate = Instant.now().epochSecond,
            modifiedDate = Instant.now().epochSecond,
            deletionDate = if (note.isDeleted) Instant.now().epochSecond else null
        )

        val newId = noteRepository.insertNote(cloned)
        tagRepository.copyTags(oldId, newId)
        reminderRepository.copyReminders(oldId, newId)

        reminderRepository
            .getByNoteId(newId)
            .first()
            .forEach {
                reminderManager.schedule(it.id, it.date, it.noteId)
            }
    }

    fun setLayoutMode(layoutMode: LayoutMode) {
        viewModelScope.launch(Dispatchers.IO) { preferenceRepository.set(layoutMode) }
    }

    fun setSortMethod(method: SortMethod) {
        viewModelScope.launch(Dispatchers.IO) { preferenceRepository.set(method) }
    }

    fun setSortTagsMethod(method: SortTagsMethod) {
        viewModelScope.launch(Dispatchers.IO) { preferenceRepository.set(method) }
    }

    fun setSortNavdrawerNotebooksMethod(method: SortNavdrawerNotebooksMethod) {
        viewModelScope.launch(Dispatchers.IO) { preferenceRepository.set(method) }
    }

    suspend fun createImageFile(): Uri? {
        val (uri, _) = mediaStorageManager.createMediaFile(type = MediaStorageManager.MediaType.IMAGE) ?: return null
        tempPhotoUri = uri
        return uri
    }

    /**
     * Creates a file for the shared media in app's private storage
     * @param uri The source URI of the media file
     * @param mimeType The MIME type of the media
     * @return The new URI in app's private storage, or null if creation failed
     */
    suspend fun copyMediaToPrivateStorage(uri: Uri, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        val mediaType = when {
            mimeType.startsWith("image/") -> MediaStorageManager.MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaStorageManager.MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaStorageManager.MediaType.AUDIO
            else -> return@withContext null
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" }
            ?: mediaType.defaultExtension

        mediaStorageManager.createMediaFile(mediaType, extension)?.first
    }

    private inline fun update(
        vararg notes: Note,
        crossinline transform: suspend (Note) -> Note,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val notes = notes
            .map { transform(it) }
            .toTypedArray()

        noteRepository.updateNotes(*notes)
    }

    private inline fun Array<out Note>.forEachAsync(crossinline block: suspend CoroutineScope.(Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { forEach { block(it) } }
    }
}
