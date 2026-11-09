package org.qosp.notes.data.sync.fs

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageBackendTitleTest {

    @Test
    fun `markdown extension is stripped`() {
        assertEquals("Shopping list", StorageBackend.titleFromFileName("Shopping list.md"))
    }

    @Test
    fun `text extension is stripped`() {
        assertEquals("Shopping list", StorageBackend.titleFromFileName("Shopping list.txt"))
    }

    @Test
    fun `only the trailing extension is stripped`() {
        assertEquals("notes.md backup", StorageBackend.titleFromFileName("notes.md backup.md"))
    }

    @Test
    fun `name without known extension is kept`() {
        assertEquals("readme", StorageBackend.titleFromFileName("readme"))
    }

    @Test
    fun `null name gives empty title`() {
        assertEquals("", StorageBackend.titleFromFileName(null))
    }

    /**
     * Regression: Google Drive document URIs end in an opaque ID rather than the file name, so the
     * title must come from the display name and never from the URI's last path segment.
     */
    @Test
    fun `display name is used rather than an opaque document id`() {
        val opaqueDocumentId = "1aBcDeFgHiJkLmNoPqRsTuVwXyZ_0123456789"
        val displayName = "Meeting notes.md"
        assertEquals("Meeting notes", StorageBackend.titleFromFileName(displayName))
        assertEquals(opaqueDocumentId, StorageBackend.titleFromFileName(opaqueDocumentId))
    }
}
