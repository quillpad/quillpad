package org.qosp.notes.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NewNoteRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.data.sync.SYNC_SCOPE
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.data.sync.neu.BackendProvider
import org.qosp.notes.data.sync.neu.SynchronizeNotes

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
        noteRepository: NoteRepository,
    ) = NotebookRepository(appDatabase.notebookDao, noteRepository, null)

    @Single
    fun provideNewNoteRepository(
        appDatabase: AppDatabase,
        backendProvider: BackendProvider,
        synchronizeNotes: SynchronizeNotes,
        @Named(NO_SYNC) notebookRepository: NotebookRepository,
        @Named(SYNC_SCOPE) syncingScope: CoroutineScope,
    ): NoteRepository =
        NewNoteRepository(
            noteDao = appDatabase.noteDao,
            idMappingDao = appDatabase.idMappingDao,
            reminderDao = appDatabase.reminderDao,
            backendProvider = backendProvider,
            synchronizeNotes = synchronizeNotes,
            notebookRepository = notebookRepository,
            syncingScope = syncingScope
        )


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
