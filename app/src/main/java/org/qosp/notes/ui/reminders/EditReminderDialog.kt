package org.qosp.notes.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.databinding.DialogEditReminderBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.requestFocusAndKeyboard
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class EditReminderDialog : BaseDialog<DialogEditReminderBinding>() {
    private val model: EditReminderViewModel by activityViewModels()

    private lateinit var reminder: Reminder
    private var noteId: Long? = null

    private var dateFormatter: DateTimeFormatter? = null
    private var timeFormatter: DateTimeFormatter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        noteId = arguments?.getLong(NOTE_ID)?.takeIf { it > 0L }
        reminder = arguments?.getParcelable(REMINDER) ?: return
    }

    override fun createBinding(inflater: LayoutInflater) = DialogEditReminderBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when {
            this::reminder.isInitialized -> {
                // Edit an existing reminder
                model.date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(reminder.date), ZoneId.systemDefault())
                dialog.setTitle(getString(R.string.reminder))
                binding.editTextReminderName.setText(reminder.name)
                binding.editTextReminderName.requestFocusAndKeyboard()

                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_delete)) { _, _ ->
                    model.deleteReminder(reminder)
                    dismiss()
                }
                setupListeners(dialog, binding, reminder)
            }
            else -> {
                val noteId = noteId ?: return
                // Create a new reminder
                dialog.setTitle(getString(R.string.action_new_reminder))
                binding.editTextReminderName.requestFocusAndKeyboard()
                setupListeners(dialog, binding, Reminder("", noteId, model.date.toEpochSecond()))
            }
        }

        model.dateTimeFormats.collect(this) { (df, tf) ->
            dateFormatter = DateTimeFormatter.ofPattern(getString(df.patternResource))
            binding.buttonSetDate.text = model.date.format(dateFormatter)

            timeFormatter = DateTimeFormatter.ofPattern(getString(tf.patternResource))
            binding.buttonSetTime.text = model.date.format(timeFormatter)
        }
    }

    private fun setupListeners(
        dialog: AlertDialog,
        binding: DialogEditReminderBinding,
        reminder: Reminder,
    ) {
        binding.buttonSetDate.setOnClickListener {
            val callback = { datePicker: DatePicker, i: Int, i1: Int, i2: Int ->
                model.setDate(year = i, month = i1 + 1, dayOfMonth = i2)
                binding.buttonSetDate.text = model.date.format(dateFormatter)
            }
            DatePickerDialog(
                requireContext(),
                callback,
                model.date.year,
                model.date.monthValue - 1,
                model.date.dayOfMonth
            ).show()
        }

        binding.buttonSetTime.setOnClickListener {
            val callback = { timePicker: TimePicker, i: Int, i1: Int ->
                model.setDate(hour = i, minute = i1)
                binding.buttonSetTime.text = model.date.format(timeFormatter)
            }
            TimePickerDialog(requireContext(), callback, model.date.hour, model.date.minute, true).show()
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
            if (model.date.isBefore(ZonedDateTime.now())) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.indicator_cannot_set_past_reminder),
                    Toast.LENGTH_SHORT
                ).show()
                return@setButton
            }
            val newReminder = reminder.copy(
                name = binding.editTextReminderName.text.toString(),
                date = model.date.toEpochSecond()
            )
            when (newReminder.id) {
                0L -> model.insertReminder(newReminder)
                else -> model.updateReminder(newReminder)
            }
            dismiss()
        }
    }

    companion object {
        private const val REMINDER = "REMINDER"
        private const val NOTE_ID = "NOTE_ID"

        fun build(noteId: Long, reminder: Reminder?): EditReminderDialog {
            return EditReminderDialog().apply {
                arguments = bundleOf(
                    REMINDER to reminder,
                    NOTE_ID to noteId,
                )
            }
        }
    }
}
