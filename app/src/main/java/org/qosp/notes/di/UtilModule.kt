package org.qosp.notes.di

import android.app.Application
import android.content.Context
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.qosp.notes.App
import org.qosp.notes.BuildConfig
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.data.sync.fs.StorageManager
import org.qosp.notes.data.sync.nextcloud.NextcloudManager
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.reminders.ReminderManager
import org.qosp.notes.ui.utils.ConnectionManager

@Module
class UtilModule {

    @Single
    fun provideMediaStorageManager(context: Context, noteRepository: NoteRepository) =
        MediaStorageManager(context, noteRepository, App.MEDIA_FOLDER)

    @Single
    fun provideReminderManager(context: Context, reminderRepository: ReminderRepository) =
        ReminderManager(context, reminderRepository)

    @Single
    fun provideSyncManager(
        context: Context,
        preferenceRepository: PreferenceRepository,
        idMappingRepository: IdMappingRepository,
        nextcloudManager: NextcloudManager,
        storageManager: StorageManager,
        app: Application,
    ) = SyncManager(
        preferenceRepository,
        idMappingRepository,
        ConnectionManager(context),
        context,
        nextcloudManager,
        storageManager,
        (app as App).syncingScope
    )

    @Single
    fun provideBackupManager(
        noteRepository: NoteRepository,
        notebookRepository: NotebookRepository,
        tagRepository: TagRepository,
        reminderRepository: ReminderRepository,
        idMappingRepository: IdMappingRepository,
        reminderManager: ReminderManager,
        context: Context,
    ) = BackupManager(
        BuildConfig.VERSION_CODE,
        noteRepository,
        notebookRepository,
        tagRepository,
        reminderRepository,
        idMappingRepository,
        reminderManager,
        context
    )
}
