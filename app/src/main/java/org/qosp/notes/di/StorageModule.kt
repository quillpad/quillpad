package org.qosp.notes.di

import android.content.Context
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.fs.StorageBackend
import org.qosp.notes.preferences.PreferenceRepository

@Module
object StorageModule {

    @Single
    fun provideStorageManager(
        preferenceRepository: PreferenceRepository,
        context: Context,
        noteRepository: NoteRepository,
        @Named(NO_SYNC) notebookRepository: NotebookRepository,
        idMappingRepository: IdMappingRepository,
    ) = StorageBackend(preferenceRepository, context, noteRepository, notebookRepository, idMappingRepository)
}
