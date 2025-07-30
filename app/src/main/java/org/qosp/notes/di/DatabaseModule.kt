package org.qosp.notes.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.qosp.notes.data.AppDatabase

object DatabaseModule {

    val dbModule = module {
        single<AppDatabase> {
            Room.databaseBuilder(
                context = androidContext(),
                klass = AppDatabase::class.java,
                name = AppDatabase.DB_NAME
            )
                // we don't want to silently wipe user data in case DB migration fails,
                // rather let the app crash
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .addMigrations(AppDatabase.MIGRATION_4_5)
                .build()
        }

        single {
            get<AppDatabase>().noteDao
        }
        single {
            get<AppDatabase>().notebookDao
        }
        single {
            get<AppDatabase>().tagDao
        }
        single {
            get<AppDatabase>().noteTagDao
        }
        single {
            get<AppDatabase>().reminderDao
        }
        single {
            get<AppDatabase>().idMappingDao
        }
    }

}
