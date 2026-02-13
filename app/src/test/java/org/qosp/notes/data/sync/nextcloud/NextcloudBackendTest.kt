package org.qosp.notes.data.sync.nextcloud

import android.util.Base64
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.nextcloud.model.NextcloudNotesCapabilities
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class NextcloudBackendTest {

    private val apiProvider = mockk<NextcloudAPIProvider>()
    private val api = mockk<NextcloudAPI>()
    private lateinit var config: NextcloudConfig
    private val timeSource = TestTimeSource()

    private lateinit var backend: NextcloudBackend

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), any()) } returns "base64"

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        config = NextcloudConfig("http://localhost", "user", "pass")
        backend = NextcloudBackend(apiProvider, config, timeSource)
        coEvery { apiProvider.getAPI() } returns api
    }

    @Test
    fun `isAvailable fetches from API on first call`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery {
            api.getNotesAPI(
                any(),
                any()
            )
        } returns emptyList() // Not used by getNotesCapabilities but good to have
        // getNotesCapabilities is an extension function, mocking it might be tricky if it's not handled by mockk properly
        // Actually, it calls getAllCapabilitiesAPI internally.
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 1) { api.getAllCapabilitiesAPI(any(), any()) }
    }

    @Test
    fun `isAvailable returns cached value within expiration period`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        // First call
        backend.isAvailable()

        // Advance time by 10 seconds (less than 30s expiration)
        timeSource += 10.seconds

        // Second call
        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 1) { api.getAllCapabilitiesAPI(any(), any()) }
    }

    @Test
    fun `isAvailable refetches after expiration period`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        // First call
        backend.isAvailable()

        // Advance time by 31 seconds (more than 30s expiration)
        timeSource += 31.seconds

        // Second call
        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 2) { api.getAllCapabilitiesAPI(any(), any()) }
    }
}
