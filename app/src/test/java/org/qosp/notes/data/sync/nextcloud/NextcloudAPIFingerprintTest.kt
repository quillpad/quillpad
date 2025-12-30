package org.qosp.notes.data.sync.nextcloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test

class NextcloudAPIFingerprintTest {

    @Test
    fun `computeFingerprint returns null for invalid host`() {
        val result = NextcloudAPIProvider.computeFingerprint("not-a-valid-url")
        assertNull(result)
    }
}