package org.qosp.notes.di

import android.content.Context
import androidx.room.Room
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.qosp.notes.data.AppDatabase

@Module
class DatabaseModule {
    @Single
    fun provideRoomDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // we don't want to silently wipe user data in case DB migration fails,
            // rather let the app crash
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
    }
}
