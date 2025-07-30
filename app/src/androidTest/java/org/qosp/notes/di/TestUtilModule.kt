package org.qosp.notes.di

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import kotlinx.coroutines.DelicateCoroutinesApi
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
import org.qosp.notes.ui.reminders.ReminderManager

const val TEST_MEDIA_FOLDER = "test_media"

object TestUtilModule {

    // Manual syncModule definition to ensure all dependencies are included
    @OptIn(DelicateCoroutinesApi::class)
    val module = module {
        single {
            MediaStorageManager(
                context = get<Context>(),
                noteRepository = get<NoteRepository>(),
                mediaFolder = TEST_MEDIA_FOLDER
            )
        }
        single {
            ReminderManager(
                context = get<Context>(),
                reminderRepository = get<ReminderRepository>(),
                noteRepository = get<NoteRepository>(),
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
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .addMigrations(AppDatabase.MIGRATION_4_5)
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
