package org.qosp.notes.tests.reminders

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.ReminderRepository
import java.time.Instant

class ReminderDeletionTest : KoinComponent {
    val noteRepository: NoteRepository by inject()
    val reminderRepository: ReminderRepository by inject()

    @Test
    @Throws(Exception::class)
    fun deletingANoteShouldAlsoDeleteItsReminders() = runBlocking {
        val note = Note(
            title = "Test Note",
            content = "Sample content"
        )
        val noteId = noteRepository.insertNote(note)

        val reminder = Reminder(
            name = "Test Reminder",
            noteId = noteId,
            date = Instant.now().epochSecond
        )

        reminderRepository.insert(reminder)
        noteRepository.deleteNotes(note.copy(id = noteId))

        assertTrue(reminderRepository.getAll().first().isEmpty())
    }
}
