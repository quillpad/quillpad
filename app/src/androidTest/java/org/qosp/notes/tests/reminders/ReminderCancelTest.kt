package org.qosp.notes.tests.reminders

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class ReminderCancelTest {
    @Inject
    lateinit var reminderManager: ReminderManager

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

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
