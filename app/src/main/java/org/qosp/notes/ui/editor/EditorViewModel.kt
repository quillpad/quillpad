package org.qosp.notes.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msoul.datastore.defaultOf
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.DateFormat
import org.qosp.notes.preferences.DefaultEditorMode
import org.qosp.notes.preferences.MoveCheckedItems
import org.qosp.notes.preferences.NewNotesSyncable
import org.qosp.notes.preferences.OpenMediaIn
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.ShowDate
import org.qosp.notes.preferences.ShowFabChangeMode
import org.qosp.notes.preferences.TimeFormat
import java.time.Instant

class EditorViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    var inEditMode: Boolean = false
    var isNotInitialized = true
    var moveCheckedItems: Boolean = true
    private val noteIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)
    var selectedRange = 0 to 0

    val data = noteIdFlow
        .filterNotNull()
        .flatMapLatest { noteRepository.getById(it) }
        .filterNotNull()
        .flatMapLatest { note ->
            getNotebookData(note.notebookId).flatMapLatest { notebook ->
                preferenceRepository.getAll().map { prefs ->
                    Data(
                        note = note,
                        notebook = notebook,
                        dateTimeFormats = prefs.dateFormat to prefs.timeFormat,
                        openMediaInternally = prefs.openMediaIn == OpenMediaIn.INTERNAL,
                        showDates = prefs.showDate == ShowDate.YES,
                        editorFontSize = prefs.editorFontSize.fontSize,
                        showFabChangeMode = prefs.showFabChangeMode == ShowFabChangeMode.FAB,
                        defaultEditorMode = prefs.defaultEditorMode,
                        isInitialized = true,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Data(),
        )

    private fun getNotebookData(notebookId: Long?): Flow<Notebook?> {
        return notebookId?.let { id -> notebookRepository.getById(id) } ?: flow { emit(null) }
    }

    fun initialize(
        noteId: Long,
        newNoteTitle: String,
        newNoteContent: String,
        newNoteAttachments: List<Attachment>,
        newNoteIsList: Boolean,
        newNoteNotebookId: Long?,
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                if (noteId > 0L) return@withContext noteId

                noteRepository.insertNote(
                    Note(
                        title = newNoteTitle,
                        content = newNoteContent,
                        notebookId = newNoteNotebookId,
                        isList = newNoteIsList,
                        attachments = newNoteAttachments,
                        isLocalOnly = preferenceRepository.get<NewNotesSyncable>().first() == NewNotesSyncable.NO
                    ),
                )
            }

            noteIdFlow.emit(id)

            isNotInitialized = false
            moveCheckedItems = preferenceRepository.get<MoveCheckedItems>().first() == MoveCheckedItems.YES
        }
    }

    fun setNoteTitle(title: String) = update { note ->
        note.copy(
            title = title,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun setNoteContent(content: String) = update { note ->
        note.copy(
            content = content,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun setColor(color: NoteColor) = update { note ->
        note.copy(
            color = color,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun deleteAttachment(attachment: Attachment) = update { note ->
        note.copy(
            attachments = note.attachments
                .filterNot { it.path == attachment.path }
                .toMutableList(),
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun insertAttachments(vararg attachments: Attachment) = update { note ->
        note.copy(
            attachments = note.attachments + attachments,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun updateTaskList(list: List<NoteTask>) = update { note ->
        note.copy(
            taskList = list,
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun toList() = update { note ->
        note.copy(
            content = "",
            isList = true,
            taskList = note.stringToTaskList(),
            modifiedDate = Instant.now().epochSecond,
        )
    }

    fun toTextNote() = update { note ->
        note.copy(
            content = note.taskListToString(),
            isList = false,
            taskList = listOf(),
            modifiedDate = Instant.now().epochSecond,
        )
    }

    private inline fun update(crossinline transform: suspend (Note) -> Note) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = data.value.note ?: return@launch
            val new = transform(note)
            noteRepository.updateNotes(new)
        }
    }

    data class Data(
        val note: Note? = null,
        val notebook: Notebook? = null,
        val dateTimeFormats: Pair<DateFormat, TimeFormat> = defaultOf<DateFormat>() to defaultOf<TimeFormat>(),
        val openMediaInternally: Boolean = true,
        val showDates: Boolean = true,
        val editorFontSize: Int = -1, // -1: not customised, default font size
        val showFabChangeMode: Boolean = true,
        val defaultEditorMode: DefaultEditorMode = defaultOf(),
        val isInitialized: Boolean = false,
        val moveCheckedItems: Boolean = true,
    )
}

private const val TAG = "EditorViewModel"
