package org.qosp.notes.di

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.GlobalScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.qosp.notes.BuildConfig.VERSION_CODE
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.data.AppDatabase
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

const val TEST_MEDIA_FOLDER = "test_media"

object TestUtilModule {

    // Manual module definition to ensure all dependencies are included
    val module = module {
        single {
            MediaStorageManager(
                context = get<Context>(),
                noteRepository = get<NoteRepository>(),
                mediaFolder = TEST_MEDIA_FOLDER
            )
        }
        single { ReminderManager(context = get<Context>(), reminderRepository = get<ReminderRepository>()) }
        single {
            SyncManager(
                preferenceRepository = get<PreferenceRepository>(),
                idMappingRepository = get<IdMappingRepository>(),
                connectionManager = ConnectionManager(get<Context>()),
                nextcloudManager = get<NextcloudManager>(),
                syncingScope = GlobalScope,
            )
        }
        single {
            BackupManager(
                currentVersion = VERSION_CODE,
                noteRepository = get<NoteRepository>(),
                notebookRepository = get<NotebookRepository>(),
                tagRepository = get<TagRepository>(),
                reminderRepository = get<ReminderRepository>(),
                idMappingRepository = get<IdMappingRepository>(),
                reminderManager = get<ReminderManager>(),
                context = get<Context>()
            )
        }
        single<AppDatabase> {
            Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .build()
        }
        single {
            MigrationTestHelper(
                instrumentation = getInstrumentation(),
                databaseClass = AppDatabase::class.java,
            )
        }
    }
}
