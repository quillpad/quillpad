package org.qosp.notes.ui.attachments.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.qosp.notes.data.repo.NoteRepository
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AttachmentDialogViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    fun getAttachment(noteId: Long, path: String) = noteRepository.getById(noteId).map { note ->
        note?.attachments?.find { it.path == path }
    }

    fun updateAttachmentDescription(noteId: Long, path: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = noteRepository.getById(noteId).first() ?: return@launch
            noteRepository.updateNotes(
                note.copy(
                    attachments = note.attachments.map {
                        if (it.path == path) it.copy(description = description)
                        else it
                    },
                    modifiedDate = Instant.now().epochSecond,
                ),
            )
        }
    }
}
