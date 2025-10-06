package org.qosp.notes.ui.utils

import android.widget.Toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ToasterTest {

    private lateinit var toaster: Toaster

    @Before
    fun setUp() {
        toaster = Toaster()
    }

    @Test
    fun `showShort emits message with Toast LENGTH_SHORT`() = runTest {
        val testMessage = "Short test message"

        val job = launch {
            val (message, time) = withTimeout(1.seconds) {
                toaster.messages.first()
            }
            assertEquals(testMessage, message)
            assertEquals(Toast.LENGTH_SHORT, time)
        }

        toaster.showShort(testMessage)
        job.join()
    }

    @Test
    fun `showLong emits message with Toast LENGTH_LONG`() = runTest {
        val testMessage = "Long test message"

        val job = launch {
            val (message, time) = withTimeout(1.seconds) {
                toaster.messages.first()
            }
            assertEquals(testMessage, message)
            assertEquals(Toast.LENGTH_LONG, time)
        }

        toaster.showLong(testMessage)
        job.join()
    }
}
