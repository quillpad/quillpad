package org.qosp.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.common.recycler.NoteRecyclerAdapter

class NoteAdapterPayloadTest {

    @Test
    fun testPayloadFlattening() {
        val payloads = listOf<Any>(
            listOf(NoteRecyclerAdapter.Payload.TitleChanged),
            listOf(NoteRecyclerAdapter.Payload.ContentChanged, NoteRecyclerAdapter.Payload.TitleChanged)
        )

        val combined = NoteRecyclerAdapter.flattenPayloads(payloads)

        assertEquals(2, combined.size)
        assertTrue(combined.contains(NoteRecyclerAdapter.Payload.TitleChanged))
        assertTrue(combined.contains(NoteRecyclerAdapter.Payload.ContentChanged))
    }

    @Test
    fun testPayloadFlatteningEmpty() {
        val combined = NoteRecyclerAdapter.flattenPayloads(emptyList())
        assertTrue(combined.isEmpty())
    }

    @Test
    fun testPayloadFlatteningMixed() {
        val payloads = listOf<Any>(
            listOf(NoteRecyclerAdapter.Payload.ColorChanged),
            "Not a payload",
            listOf(NoteRecyclerAdapter.Payload.TagsChanged)
        )
        val combined = NoteRecyclerAdapter.flattenPayloads(payloads)
        assertEquals(2, combined.size)
        assertTrue(combined.contains(NoteRecyclerAdapter.Payload.ColorChanged))
        assertTrue(combined.contains(NoteRecyclerAdapter.Payload.TagsChanged))
    }
}
