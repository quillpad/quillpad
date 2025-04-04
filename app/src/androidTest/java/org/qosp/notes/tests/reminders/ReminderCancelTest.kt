package org.qosp.notes.tests.reminders

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant

class ReminderCancelTest : KoinComponent {
    val reminderManager: ReminderManager by inject()

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
