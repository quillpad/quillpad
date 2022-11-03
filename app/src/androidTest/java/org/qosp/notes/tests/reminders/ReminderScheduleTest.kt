package org.qosp.notes.tests.reminders

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.ui.reminders.ReminderManager
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class ReminderScheduleTest {
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
    fun reminderIsScheduledCorrectly() {
        val (reminderId, noteId) = 1L to 1L
        reminderManager.schedule(reminderId, Instant.now().plusSeconds(3600).epochSecond, noteId)
        assertTrue(reminderManager.isReminderSet(reminderId, noteId))
    }
}
