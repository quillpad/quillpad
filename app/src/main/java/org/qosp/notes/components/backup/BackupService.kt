package org.qosp.notes.components.backup

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qosp.notes.App
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.BackupStrategy
import org.qosp.notes.preferences.PreferenceRepository
import javax.inject.Inject

@AndroidEntryPoint
class BackupService : LifecycleService() {
    private var nextId = 0
        get() {
            field += 1
            return "backup_$field".hashCode()
        }

    private val jobs = mutableListOf<Job>()

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var backupManager: BackupManager

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { intent ->
            val action = intent.extras?.getSerializable(ACTION) as? Action ?: return@let
            val uri = intent.extras?.getParcelable<Uri>(URI_EXTRA) ?: return@let

            when (action) {
                Action.RESTORE -> {
                    restoreNotes(uri)
                }
                Action.BACKUP -> {
                    val notes = intent.extras?.getParcelableArrayList<Note>(NOTES)?.toSet()
                    backupNotes(notes, uri)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    private fun startJob(block: suspend CoroutineScope.() -> Unit) {
        val job = lifecycleScope.launch(Dispatchers.IO, block = block)
        job.invokeOnCompletion {
            if (jobs.all { it.isCompleted }) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            jobs.remove(job)
        }
        jobs.add(job)
    }

    /**
     * Backs up the specific [notes] to a file with URI [outputUri] or all notes if [notes] is null.
     */
    private fun backupNotes(notes: Set<Note>? = null, outputUri: Uri) = startJob {
        val handler = when (preferenceRepository.get<BackupStrategy>().first()) {
            BackupStrategy.INCLUDE_FILES -> AttachmentHandler.IncludeFiles(applicationContext)
            BackupStrategy.KEEP_INFO -> AttachmentHandler.KeepInfoOnly
            BackupStrategy.KEEP_NOTHING -> AttachmentHandler.KeepNothing
        }

        val backup = backupManager.createBackup(
            notes = notes,
            attachmentHandler = handler
        )

        val notificationId = nextId
        val notificationBuilder = NotificationCompat.Builder(applicationContext, App.BACKUPS_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_backing_up))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setOngoing(true)

        val progressHandler = object : ProgressHandler {
            override fun onProgressChanged(current: Int, max: Int) {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder.setProgress(max, current, false).build()
                )
            }

            override fun onCompletion() {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder
                        .setContentTitle(getString(R.string.notification_backup_complete))
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .build()
                )
            }

            override fun onFailure(e: Throwable) {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder
                        .setContentTitle(getString(R.string.notification_backup_failed))
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .build()
                )
            }
        }

        startForeground(notificationId, notificationBuilder.build())

        backupManager.createBackupZipFile(
            backup.serialize(),
            handler,
            outputUri,
            progressHandler
        )
    }

    private fun restoreNotes(backupUri: Uri) = startJob {
        val notificationId = nextId
        val notificationBuilder = NotificationCompat.Builder(applicationContext, App.BACKUPS_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_restoring_notes))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setOngoing(true)
        val notification = notificationBuilder.build()

        startForeground(notificationId, notification)

        val backup = backupManager.backupFromZipFile(backupUri, DefaultMigrationHandler()).fold(
            onSuccess = { it },
            onFailure = {
                val notification = notificationBuilder
                    .setContentTitle(getString(R.string.notification_restore_failed))
                    .setContentText(it.message)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()

                notificationManager?.notify(notificationId, notification)
                return@startJob
            },
        )

        backupManager.restoreNotesFromBackup(backup)

        notificationManager?.notify(
            notificationId,
            notificationBuilder
                .setContentTitle(getString(R.string.notification_restore_complete))
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
    }

    enum class Action {
        RESTORE,
        BACKUP,
    }

    companion object {
        private const val URI_EXTRA = "URI_EXTRA"
        private const val NOTES = "NOTES"
        private const val ACTION = "ACTION"

        fun restoreNotes(context: Context, backupUri: Uri) {
            Intent(context, BackupService::class.java).also { intent ->
                intent.putExtra(ACTION, Action.RESTORE)
                intent.putExtra(URI_EXTRA, backupUri)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun backupNotes(context: Context, notes: Set<Note>? = null, outputUri: Uri) {
            Intent(context, BackupService::class.java).also { intent ->
                intent.putExtra(ACTION, Action.BACKUP)
                if (notes != null) intent.putParcelableArrayListExtra(NOTES, ArrayList(notes))
                intent.putExtra(URI_EXTRA, outputUri)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
