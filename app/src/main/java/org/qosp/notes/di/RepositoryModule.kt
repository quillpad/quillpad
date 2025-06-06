package org.qosp.notes.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NewNoteRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository

const val NO_SYNC = "NO_SYNC"

object RepositoryModule {

    val module = module {
        includes(DatabaseModule.module)

        single(named(NO_SYNC)) {
            NotebookRepository(
                notebookDao = get<AppDatabase>().notebookDao,
                noteRepository = get(),
                syncManager = null
            )
        }
        single {
            NotebookRepository(
                notebookDao = get<AppDatabase>().notebookDao,
                noteRepository = get(),
                syncManager = get()
            )
        }
        single<NoteRepository> {
            NewNoteRepository(
                noteDao = get<AppDatabase>().noteDao,
                idMappingDao = get<AppDatabase>().idMappingDao,
                reminderDao = get<AppDatabase>().reminderDao,
                backendProvider = get(),
                synchronizeNotes = get(),
                notebookRepository = get(),
                syncingScope = get()
            )
        }

        single {
            ReminderRepository(get<AppDatabase>().reminderDao)
        }

        single {
            TagRepository(
                tagDao = get<AppDatabase>().tagDao,
                noteTagDao = get<AppDatabase>().noteTagDao,
                noteRepository = get(),
                syncManager = get()
            )
        }
        single {
            IdMappingRepository(get<AppDatabase>().idMappingDao)
        }
    }
}
