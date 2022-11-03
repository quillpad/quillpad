package org.qosp.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note

class NoteUnitTests {
    @Test
    fun defaultNoteShouldBeEmpty() {
        assertEquals(Note().isEmpty(), true)
    }

    @Test
    fun noteWithAttachmentsShouldNotBeEmpty() {
        assertEquals(Note(attachments = listOf(Attachment())).isEmpty(), false)
    }
}
