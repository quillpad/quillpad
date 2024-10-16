package org.qosp.notes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.msoul.datastore.defaultOf
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.BaseResult
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.preferences.GroupNotesWithoutNotebook
import org.qosp.notes.preferences.LayoutMode
import org.qosp.notes.preferences.NoteDeletionTime
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.preferences.SortNavdrawerNotebooksMethod
import org.qosp.notes.preferences.SortTagsMethod
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val preferenceRepository: PreferenceRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderManager: ReminderManager,
    private val tagRepository: TagRepository,
    private val mediaStorageManager: MediaStorageManager,
    private val syncManager: SyncManager,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
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

    fun syncAsync(): Deferred<BaseResult> {
        return syncManager.syncingScope.async {
            syncManager.sync()
        }
    }

    fun sync() {
        syncManager.syncingScope.launch {
            syncManager.sync()
        }
    }

    fun discardEmptyNotesAsync() = viewModelScope.async(Dispatchers.IO) {
        noteRepository.discardEmptyNotes()
    }

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
            noteRepository.restoreNotes(*notes)
        }
    }

    fun archiveNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isArchived = true,
        )
    }

    fun unarchiveNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isArchived = false,
        )
    }

    fun showNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isHidden = false,
        )
    }

    fun hideNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isHidden = true,
        )
    }

    fun pinNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isPinned = !note.isPinned,
        )
    }

    fun compactPreviewNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isCompactPreview = true,
        )
    }

    fun fullPreviewNotes(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isCompactPreview = false,
        )
    }

    fun moveNotes(notebookId: Long?, vararg notes: Note) = update(*notes) { note ->
        note.copy(
            notebookId = notebookId,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun makeNotesSyncable(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isLocalOnly = false,
        )
    }

    fun makeNotesLocal(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isLocalOnly = true,
        )
    }

    fun makeNotesFullPreview(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isCompactPreview = false,
        )
    }

    fun makeNotesCompactPreview(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isCompactPreview = true,
        )
    }

    fun disableScreenAlwaysOn(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            screenAlwaysOn = false,
        )
    }

    fun enableScreenAlwaysOn(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            screenAlwaysOn = true,
        )
    }

    fun disableMarkdown(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isMarkdownEnabled = false,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun enableMarkdown(vararg notes: Note) = update(*notes) { note ->
        note.copy(
            isMarkdownEnabled = true,
            modifiedDate = Instant.now().epochSecond,
        )
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.set(layoutMode)
        }
    }

    fun setSortMethod(method: SortMethod) {
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.set(method)
        }
    }

    fun setSortTagsMethod(method: SortTagsMethod) {
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.set(method)
        }
    }

    fun setSortNavdrawerNotebooksMethod(method: SortNavdrawerNotebooksMethod) {
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.set(method)
        }
    }

    suspend fun createImageFile(): Uri? {
        val (uri, _) = mediaStorageManager.createMediaFile(type = MediaStorageManager.MediaType.IMAGE) ?: return null
        tempPhotoUri = uri
        return uri
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
        viewModelScope.launch(Dispatchers.IO) {
            forEach { block(it) }
        }
    }
}
