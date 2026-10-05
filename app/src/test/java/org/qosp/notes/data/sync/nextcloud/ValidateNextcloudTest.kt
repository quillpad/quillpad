package org.qosp.notes.data.sync.nextcloud

import android.util.Base64
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.acra.ktx.sendWithAcra
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.sync.nextcloud.model.NextcloudCapabilitiesResult
import org.qosp.notes.data.sync.nextcloud.model.NextcloudCapabilitiesResultCapabilities
import org.qosp.notes.data.sync.nextcloud.model.NextcloudCapabilitiesResultData
import org.qosp.notes.data.sync.nextcloud.model.NextcloudCapabilitiesResultOcs
import org.qosp.notes.data.sync.nextcloud.model.NextcloudNotesCapabilities
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ValidateNextcloudTest {

    private val apiProvider = mockk<NextcloudAPIProvider>()
    private val api = mockk<NextcloudAPI>()
    private lateinit var config: NextcloudConfig
    private val validate = ValidateNextcloud(apiProvider)

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), any()) } returns "base64"
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        mockkStatic("org.acra.ktx.ExtensionsKt")
        every { any<Throwable>().sendWithAcra() } returns Unit
        config = NextcloudConfig("https://localhost/", "user", "pass")
        coEvery { apiProvider.getAPI() } returns api
    }

    @After
    fun tearDown() = unmockkAll()

    private fun capabilities(notes: NextcloudNotesCapabilities?) = NextcloudCapabilitiesResult(
        NextcloudCapabilitiesResultOcs(
            NextcloudCapabilitiesResultData(NextcloudCapabilitiesResultCapabilities(notes)),
        ),
    )

    private fun http(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

    private suspend fun resultFor(error: Throwable): BackendValidationResult {
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } throws error
        return validate(config)
    }

    @Test
    fun `returns Success for a compatible server`() = runTest {
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns
            capabilities(NextcloudNotesCapabilities(apiVersion = listOf("1.0", "1.2"), version = "4.10"))
        assertEquals(BackendValidationResult.Success, validate(config))
    }

    @Test
    fun `returns NotesNotInstalled when capability is missing`() = runTest {
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns capabilities(null)
        assertEquals(BackendValidationResult.NotesNotInstalled, validate(config))
    }

    @Test
    fun `401 and 403 are reported as invalid credentials`() = runTest {
        assertEquals(BackendValidationResult.InvalidConfig, resultFor(http(401)))
        assertEquals(BackendValidationResult.InvalidConfig, resultFor(http(403)))
    }

    @Test
    fun `other http errors are not reported as invalid credentials`() = runTest {
        assertEquals(BackendValidationResult.UnexpectedError, resultFor(http(500)))
        assertEquals(BackendValidationResult.UnexpectedError, resultFor(http(404)))
    }

    @Test
    fun `ssl errors are reported as certificate errors`() = runTest {
        assertEquals(BackendValidationResult.CertificateError, resultFor(SSLHandshakeException("bad cert")))
    }

    @Test
    fun `network errors are reported as connection errors`() = runTest {
        assertEquals(BackendValidationResult.ConnectionError, resultFor(SocketTimeoutException("timeout")))
        assertEquals(BackendValidationResult.ConnectionError, resultFor(IOException("unreachable")))
    }

    @Test
    fun `unexpected exceptions are not reported as invalid credentials`() = runTest {
        assertEquals(BackendValidationResult.UnexpectedError, resultFor(IllegalStateException("boom")))
    }
}
