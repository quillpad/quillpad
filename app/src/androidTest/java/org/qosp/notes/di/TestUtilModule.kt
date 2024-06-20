package org.qosp.notes.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.GlobalScope
import org.qosp.notes.BuildConfig
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.data.sync.fs.StorageBackend
import org.qosp.notes.data.sync.nextcloud.NextcloudBackend
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.reminders.ReminderManager
import org.qosp.notes.ui.utils.ConnectionManager
import javax.inject.Provider

const val TEST_MEDIA_FOLDER = "test_media"

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [UtilModule::class],
)
object TestUtilModule {

    @Provides
    @Singleton
    fun provideMediaStorageManager(
        @ApplicationContext context: Context,
        noteRepository: NoteRepository,
    ) = MediaStorageManager(context, noteRepository, TEST_MEDIA_FOLDER)

    @Provides
    @Singleton
    fun provideReminderManager(
        @ApplicationContext context: Context,
        reminderRepository: ReminderRepository,
    ) = ReminderManager(context, reminderRepository)

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        preferenceRepository: PreferenceRepository,
        idMappingRepository: IdMappingRepository,
        nextcloudBackend: NextcloudBackend,
        storageBackend: StorageBackend,
        noteRepository: Provider<NoteRepository>,
        notebookRepository: Provider<NotebookRepository>
    ): SyncManager = SyncManager(
        preferenceRepository,
        idMappingRepository,
        ConnectionManager(context),
        notebookRepository,
        noteRepository,
        context,
        nextcloudBackend,
        storageBackend,
        GlobalScope,
    )

    @Provides
    @Singleton
    fun provideBackupManager(
        noteRepository: NoteRepository,
        notebookRepository: NotebookRepository,
        tagRepository: TagRepository,
        reminderRepository: ReminderRepository,
        idMappingRepository: IdMappingRepository,
        reminderManager: ReminderManager,
        @ApplicationContext context: Context,
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
