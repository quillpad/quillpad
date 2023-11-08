package org.qosp.notes.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.fs.StorageBackend
import org.qosp.notes.preferences.PreferenceRepository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideStorageManager(
        preferenceRepository: PreferenceRepository,
        @ApplicationContext context: Context,
        @Named(NO_SYNC) noteRepository: NoteRepository,
        @Named(NO_SYNC) notebookRepository: NotebookRepository,
        idMappingRepository: IdMappingRepository,
    ) = StorageBackend(preferenceRepository, context, noteRepository, notebookRepository, idMappingRepository)
}
