package org.qosp.notes.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.core.SyncManager

const val NO_SYNC = "NO_SYNC"

@Module
class RepositoryModule {
    @Single
    fun provideNotebookRepository(
        appDatabase: AppDatabase,
        noteRepository: NoteRepository,
        syncManager: SyncManager,
    ) = NotebookRepository(appDatabase.notebookDao, noteRepository, syncManager)

    @Named(NO_SYNC)
    @Single
    fun provideNotebookRepositoryWithNullSyncManager(
        appDatabase: AppDatabase,
        @Named(NO_SYNC) noteRepository: NoteRepository,
    ) = NotebookRepository(appDatabase.notebookDao, noteRepository, null)

    @Single
    fun provideNoteRepository(
        appDatabase: AppDatabase,
        syncManager: SyncManager,
    ) = NoteRepository(appDatabase.noteDao, appDatabase.idMappingDao, appDatabase.reminderDao, syncManager)


    @Named(NO_SYNC)
    @Single
    fun provideNoteRepositoryWithNullSyncManager(
        appDatabase: AppDatabase,
    ) = NoteRepository(appDatabase.noteDao, appDatabase.idMappingDao, appDatabase.reminderDao, null)


    @Single
    fun provideReminderRepository(appDatabase: AppDatabase) = ReminderRepository(appDatabase.reminderDao)


    @Single
    fun provideTagRepository(
        appDatabase: AppDatabase,
        syncManager: SyncManager,
        noteRepository: NoteRepository,
    ) = TagRepository(appDatabase.tagDao, appDatabase.noteTagDao, noteRepository, syncManager)


    @Single
    fun provideCloudIdRepository(appDatabase: AppDatabase) = IdMappingRepository(appDatabase.idMappingDao)
}
