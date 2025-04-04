package org.qosp.notes.tests.reminders

import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant

class ReminderScheduleTest : KoinComponent {
    private val reminderManager: ReminderManager by inject()

    @Test
    @Throws(Exception::class)
    fun reminderIsScheduledCorrectly() {
        val (reminderId, noteId) = 1L to 1L
        reminderManager.schedule(reminderId, Instant.now().plusSeconds(3600).epochSecond, noteId)
        assertTrue(reminderManager.isReminderSet(reminderId, noteId))
    }
}
