package org.qosp.notes.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.preferences.PreferenceRepository
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class EditReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderManager: ReminderManager,
    preferenceRepository: PreferenceRepository
) : ViewModel() {

    var date = ZonedDateTime.now()

    val dateTimeFormats = preferenceRepository.getAll().map { it.dateFormat to it.timeFormat }

    fun insertReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = reminderRepository.insert(reminder)
            reminderManager.schedule(id, reminder.date, reminder.noteId)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.deleteById(reminder.id)
        }
        reminderManager.cancel(reminder.id, reminder.noteId)
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.update(reminder)
        }
        reminderManager.schedule(reminder.id, reminder.date, reminder.noteId)
    }

    fun setDate(
        year: Int = date.year,
        month: Int = date.monthValue,
        dayOfMonth: Int = date.dayOfMonth,
        hour: Int = date.hour,
        minute: Int = date.minute,
    ) {
        date = date
            .withYear(year)
            .withMonth(month)
            .withDayOfMonth(dayOfMonth)
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
    }
}
