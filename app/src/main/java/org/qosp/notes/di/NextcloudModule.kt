package org.qosp.notes.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import org.qosp.notes.data.sync.nextcloud.NextcloudManager
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NextcloudModule {

    @Provides
    @Singleton
    fun provideNextcloud(): NextcloudAPI {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Since the URL is configurable by the user we set it later during the request
            .addConverterFactory(
                Json {
                    ignoreUnknownKeys = true
                }
                    .asConverterFactory(MediaType.get("application/json"))
            )
            .build()
            .create()
    }

    @Provides
    @Singleton
    fun provideNextcloudManager(
        nextcloudAPI: NextcloudAPI,
        @Named(NO_SYNC) noteRepository: NoteRepository,
        @Named(NO_SYNC) notebookRepository: NotebookRepository,
        idMappingRepository: IdMappingRepository,
    ) = NextcloudManager(nextcloudAPI, noteRepository, notebookRepository, idMappingRepository)
}
