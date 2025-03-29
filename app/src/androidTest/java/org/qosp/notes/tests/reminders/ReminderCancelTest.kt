package org.qosp.notes.tests.reminders

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant
import javax.inject.Inject

class ReminderCancelTest {
    @Inject
    lateinit var reminderManager: ReminderManager

    @Test
    @Throws(Exception::class)
    fun reminderIsCancelledCorrectly() = runBlocking {
        val (reminderId, noteId) = 1L to 1L
        reminderManager.schedule(reminderId, Instant.now().plusSeconds(3600).epochSecond, noteId)
        assertTrue("Reminder could not be scheduled", reminderManager.isReminderSet(reminderId, noteId))
        reminderManager.cancel(reminderId, noteId)
        assertTrue("Reminder could not be cancelled", !reminderManager.isReminderSet(reminderId, noteId))
    }
}
