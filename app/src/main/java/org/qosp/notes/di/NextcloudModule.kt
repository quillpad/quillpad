package org.qosp.notes.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qosp.notes.data.sync.nextcloud.NextcloudAPI
import org.qosp.notes.data.sync.nextcloud.ValidateNextcloud
import retrofit2.Retrofit
import retrofit2.create


object NextcloudModule {
    private val json = Json { ignoreUnknownKeys = true }

    val nextcloudModule = module {
        single { getRetrofitted<NextcloudAPI>(client = get()) }
        single<OkHttpClient> {
            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
            interceptor.redactHeader("Authorization")
            interceptor.redactHeader("Cookie")
            interceptor.redactHeader("Set-Cookie")
            OkHttpClient.Builder().addInterceptor(interceptor).build()
        }

        singleOf(::ValidateNextcloud)
    }


    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> getRetrofitted(client: OkHttpClient): T {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Since the URL is configurable by the user we set it later during the request
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .client(client)
            .build()
            .create()
    }
}
