package org.qosp.notes.ui.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder
import kotlinx.coroutines.flow.first
import org.qosp.notes.App
import org.qosp.notes.R
import org.qosp.notes.data.repo.ReminderRepository

class ReminderManager(
    private val context: Context,
    private val reminderRepository: ReminderRepository,
) {
    private fun requestBroadcast(reminderId: Long, noteId: Long, flag: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val defaultFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val notificationIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtras(
                bundleOf(
                    "noteId" to noteId,
                    "reminderId" to reminderId,
                )
            )
            action = ReminderReceiver.REMINDER_HAS_FIRED
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            notificationIntent,
            flag or defaultFlag
        )
    }

    fun isReminderSet(reminderId: Long, noteId: Long): Boolean {
        val defaultFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return requestBroadcast(reminderId, noteId, PendingIntent.FLAG_NO_CREATE or defaultFlag) != null
    }

    fun schedule(reminderId: Long, dateTime: Long, noteId: Long) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val broadcast = requestBroadcast(reminderId, noteId) ?: return

        cancel(reminderId, noteId, keepIntent = true)
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            dateTime * 1000, // convert seconds to millis
            broadcast
        )
    }

    fun cancel(reminderId: Long, noteId: Long, keepIntent: Boolean = false) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val defaultFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val broadcast = requestBroadcast(reminderId, noteId, PendingIntent.FLAG_NO_CREATE or defaultFlag) ?: return
        alarmManager.cancel(broadcast)
        if (!keepIntent) broadcast.cancel()
    }

    suspend fun cancelAllRemindersForNote(noteId: Long) {
        val reminders = reminderRepository.getByNoteId(noteId).first()
        reminders.forEach { cancel(it.id, noteId) }
    }

    suspend fun rescheduleAll() {
        reminderRepository
            .getAll()
            .first()
            .forEach { reminder ->
                if (reminder.hasExpired()) {
                    reminderRepository.deleteById(reminder.id)
                    return@forEach
                }
                schedule(reminder.id, reminder.date, reminder.noteId)
            }
    }

    suspend fun sendNotification(reminderId: Long, noteId: Long) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        var notificationTitle = ""

        reminderRepository.getById(reminderId).first()?.let { notificationTitle = it.name }
        reminderRepository.deleteById(reminderId)

        val pendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.fragment_editor)
            .setArguments(
                bundleOf(
                    "noteId" to noteId,
                    "transitionName" to ""
                )
            )
            .createPendingIntent()

        val notification = NotificationCompat.Builder(context, App.REMINDERS_CHANNEL_ID)
            .setContentText(notificationTitle)
            .setContentTitle(context.getString(R.string.notification_reminder_fired))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminderId.toInt(), notification)
    }
}
