package org.qosp.notes.data.sync.nextcloud

import android.annotation.SuppressLint
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.TrustSelfSignedCertificate
import retrofit2.Retrofit
import retrofit2.create
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NextcloudAPIProvider(private val preferenceRepository: PreferenceRepository) {
    private val json = Json { ignoreUnknownKeys = true }


    private val defaultClient: NextcloudAPI by lazy {
        builder().client(createDefaultClient()).build().create()
    }

    private val selfSignedTrustingClient: NextcloudAPI by lazy {
        builder().client(createSelfSignedTrustingClient()).build().create()
    }

    private fun builder(): Retrofit.Builder = Retrofit.Builder()
        .baseUrl("http://localhost/") // Since the URL is configurable by the user we set it later during the request
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        )

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getAPI(): NextcloudAPI {
        val preference = preferenceRepository.get<TrustSelfSignedCertificate>().first()
        return when (preference) {
            TrustSelfSignedCertificate.YES -> selfSignedTrustingClient
            TrustSelfSignedCertificate.NO -> defaultClient
        }
    }

    private fun createDefaultClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("Cookie")
        interceptor.redactHeader("Set-Cookie")

        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @SuppressLint("TrustAllX509TrustManager")
    private fun createSelfSignedTrustingClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("Cookie")
        interceptor.redactHeader("Set-Cookie")

        // Create a trust manager that accepts all certificates
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

        // Create SSL context that uses our custom trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder().addInterceptor(interceptor)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Accept all hostnames
            .build()
    }
}
