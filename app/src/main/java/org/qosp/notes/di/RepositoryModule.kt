package org.qosp.notes.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.SyncManager
import javax.inject.Named
import javax.inject.Singleton

const val NO_SYNC = "NO_SYNC"
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideNotebookRepository(
        appDatabase: AppDatabase,
        noteRepository: NoteRepository,
        syncManager: SyncManager,
    ) = NotebookRepository(appDatabase.notebookDao, noteRepository, syncManager)

    @Provides
    @Named(NO_SYNC)
    @Singleton
    fun provideNotebookRepositoryWithNullSyncManager(
        appDatabase: AppDatabase,
        @Named(NO_SYNC) noteRepository: NoteRepository,
    ) = NotebookRepository(appDatabase.notebookDao, noteRepository, null)

    @Provides
    @Singleton
    fun provideNoteRepository(
        appDatabase: AppDatabase,
        syncManager: SyncManager,
    ) = NoteRepository(appDatabase.noteDao, appDatabase.idMappingDao, appDatabase.reminderDao, syncManager)

    @Provides
    @Named(NO_SYNC)
    @Singleton
    fun provideNoteRepositoryWithNullSyncManager(
        appDatabase: AppDatabase,
    ) = NoteRepository(appDatabase.noteDao, appDatabase.idMappingDao, appDatabase.reminderDao, null)

    @Provides
    @Singleton
    fun provideReminderRepository(appDatabase: AppDatabase) = ReminderRepository(appDatabase.reminderDao)

    @Provides
    @Singleton
    fun provideTagRepository(
        appDatabase: AppDatabase,
        syncManager: SyncManager,
        noteRepository: NoteRepository,
    ) = TagRepository(appDatabase.tagDao, appDatabase.noteTagDao, noteRepository, syncManager)

    @Provides
    @Singleton
    fun provideCloudIdRepository(appDatabase: AppDatabase) = IdMappingRepository(appDatabase.idMappingDao)
}
