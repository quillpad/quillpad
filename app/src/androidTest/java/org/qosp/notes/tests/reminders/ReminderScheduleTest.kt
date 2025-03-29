package org.qosp.notes.tests.reminders

import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant
import javax.inject.Inject

class ReminderScheduleTest {
    @Inject
    lateinit var reminderManager: ReminderManager


    @Test
    @Throws(Exception::class)
    fun reminderIsScheduledCorrectly() {
        val (reminderId, noteId) = 1L to 1L
        reminderManager.schedule(reminderId, Instant.now().plusSeconds(3600).epochSecond, noteId)
        assertTrue(reminderManager.isReminderSet(reminderId, noteId))
    }
}
