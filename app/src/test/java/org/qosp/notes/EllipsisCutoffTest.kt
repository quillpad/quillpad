package org.qosp.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.ui.utils.ellipsisCutoff

class EllipsisCutoffTest {

    @Test
    fun `cuts three characters before the end of the last visible line`() {
        assertEquals(97, ellipsisCutoff(lineEnd = 100, textLength = 127))
    }

    @Test
    fun `line end shorter than the ellipsis does not go negative`() {
        assertEquals(0, ellipsisCutoff(lineEnd = 2, textLength = 10))
        assertEquals(0, ellipsisCutoff(lineEnd = 0, textLength = 10))
    }

    @Test
    fun `invalid line end of minus one does not go negative`() {
        assertEquals(0, ellipsisCutoff(lineEnd = -1, textLength = 127))
    }

    @Test
    fun `stale layout longer than the text is clamped to text length`() {
        assertEquals(5, ellipsisCutoff(lineEnd = 200, textLength = 5))
    }

    @Test
    fun `empty text always yields zero`() {
        assertEquals(0, ellipsisCutoff(lineEnd = 10, textLength = 0))
        assertEquals(0, ellipsisCutoff(lineEnd = -1, textLength = 0))
    }
}
