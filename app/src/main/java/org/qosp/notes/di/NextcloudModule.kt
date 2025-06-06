package org.qosp.notes.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qosp.notes.data.sync.neu.ValidateNextcloud
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import retrofit2.Retrofit
import retrofit2.create

object NextcloudModule {
    private val json = Json { ignoreUnknownKeys = true }

    val nextcloudModule = module {
        single { getRetrofitted<NextcloudAPI>() }

        singleOf(::ValidateNextcloud)
    }


    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> getRetrofitted(): T {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Since the URL is configurable by the user we set it later during the request
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create()
    }

}
