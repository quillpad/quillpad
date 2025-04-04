package org.qosp.notes.di

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.GlobalScope
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.qosp.notes.BuildConfig
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.data.sync.nextcloud.NextcloudManager
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.reminders.ReminderManager
import org.qosp.notes.ui.utils.ConnectionManager

const val TEST_MEDIA_FOLDER = "test_media"

@Module
class TestUtilModule {


    @Single
    fun provideMediaStorageManager(
        context: Context,
        noteRepository: NoteRepository,
    ) = MediaStorageManager(context, noteRepository, TEST_MEDIA_FOLDER)

    @Single
    fun provideReminderManager(
        context: Context,
        reminderRepository: ReminderRepository,
    ) = ReminderManager(context, reminderRepository)

    @Single
    fun provideSyncManager(
        context: Context,
        preferenceRepository: PreferenceRepository,
        idMappingRepository: IdMappingRepository,
        nextcloudManager: NextcloudManager,
    ) = SyncManager(
        preferenceRepository,
        idMappingRepository,
        ConnectionManager(context),
        nextcloudManager,
        GlobalScope,
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

    @Single
    fun provideMigrationTestHelper() = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )
}
