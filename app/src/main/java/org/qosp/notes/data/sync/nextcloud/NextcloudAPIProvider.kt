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
import org.qosp.notes.BuildConfig
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

    private fun createPinnedClient(fingerprintHexUpperCase: String): OkHttpClient {
        // Pinned client: verify that the server's leaf certificate SHA-256 fingerprint matches expected value
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE)
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("Cookie")
        interceptor.redactHeader("Set-Cookie")

        val trustManager = object : X509TrustManager {
            private val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // not used
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw IllegalArgumentException("Empty certificate chain")
                val cert = chain[0]
                val digest = sha256.digest(cert.encoded)
                val hex = digest.joinToString("") { "%02X".format(it) }
                if (!hex.equals(fingerprintHexUpperCase, ignoreCase = true)) {
                    throw javax.net.ssl.SSLPeerUnverifiedException("Pinned certificate fingerprint mismatch")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }

    private fun builder(): Retrofit.Builder = Retrofit.Builder()
        .baseUrl("http://localhost/") // Since the URL is configurable by the user we set it later during the request
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        )

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getAPI(): NextcloudAPI {
        // If a pinned fingerprint exists, prefer the pinned client (TOFU/pinning flow)
        val pinned = preferenceRepository.getEncryptedString(PreferenceRepository.NEXTCLOUD_CERT_FINGERPRINT).first()
        if (pinned.isNotBlank()) {
            return builder().client(createPinnedClient(pinned)).build().create()
        }

        val preference = preferenceRepository.get<TrustSelfSignedCertificate>().first()
        return when (preference) {
            TrustSelfSignedCertificate.YES -> selfSignedTrustingClient
            TrustSelfSignedCertificate.NO -> defaultClient
        }
    }

    /**
     * Connects to the server (without validating trust) to obtain the presented leaf certificate and
     * returns its SHA-256 fingerprint as uppercase hex string, or null on failure.
     * Note: this uses a permissive TLS socket only to fetch the certificate for TOFU workflows.
     */
    fun getServerCertificateFingerprint(instanceUrl: String): String? {
        return computeFingerprint(instanceUrl)
    }

    companion object {
        fun computeFingerprint(instanceUrl: String): String? {
            try {
                val uri = java.net.URI(instanceUrl)
                val host = uri.host ?: instanceUrl
                val port = if (uri.port == -1) 443 else uri.port
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(
                    @SuppressLint("CustomX509TrustManager")
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }), java.security.SecureRandom())

                val factory = sslContext.socketFactory
                val socket = factory.createSocket() as javax.net.ssl.SSLSocket
                socket.soTimeout = 5000
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                socket.startHandshake()
                val session = socket.session
                val chain = session.peerCertificates
                if (chain.isNotEmpty()) {
                    val cert = chain[0] as X509Certificate
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    val hex = digest.joinToString("") { "%02X".format(it) }
                    socket.close()
                    return hex
                }
                socket.close()
                return null
            } catch (e: Exception) {
                return null
            }
        }
    }

    private fun createDefaultClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE)
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("Cookie")
        interceptor.redactHeader("Set-Cookie")

        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @SuppressLint("TrustAllX509TrustManager")
    private fun createSelfSignedTrustingClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE)
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("Cookie")
        interceptor.redactHeader("Set-Cookie")

        // WARNING: This TrustManager accepts all certificates and hostname verification is disabled.
        // This is an *insecure* fallback for users who explicitly opt-in to trust self-signed certificates.
        // Prefer implementing certificate pinning or TOFU (trust-on-first-use) instead of accepting
        // all certificates in production builds.

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
            .hostnameVerifier { _, _ -> true } // Accept all hostnames - very unsafe, kept for compatibility only
            .build()
    }
}
