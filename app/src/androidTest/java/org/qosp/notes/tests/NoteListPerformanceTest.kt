package org.qosp.notes.tests

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.data.model.Note
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.ui.common.recycler.NoteRecyclerAdapter
import org.qosp.notes.ui.common.recycler.NoteViewHolder

@RunWith(AndroidJUnit4::class)
class NoteListPerformanceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(org.qosp.notes.R.style.AppTheme)
    }

    @Test
    fun testNoteViewHolderTitleUpdateViaPayload() {
        val binding = LayoutNoteBinding.inflate(LayoutInflater.from(context))
        val viewHolder = NoteViewHolder(
            binding = binding,
            listener = null,
            context = context,
            searchMode = false,
            markwon = mockk(relaxed = true),
            tasksViewPool = RecyclerView.RecycledViewPool(),
            attachmentsViewPool = RecyclerView.RecycledViewPool()
        )

        val note = Note(title = "Old Title")
        viewHolder.bind(note)
        assertEquals("Old Title", binding.textViewTitle.text.toString())

        val updatedNote = note.copy(title = "New Title")
        viewHolder.runPayloads(updatedNote, setOf(NoteRecyclerAdapter.Payload.TitleChanged))
        assertEquals("New Title", binding.textViewTitle.text.toString())
    }

    @Test
    fun testNoteViewHolderContentUpdateViaPayload_NonMarkdown() {
        val binding = LayoutNoteBinding.inflate(LayoutInflater.from(context))
        val viewHolder = NoteViewHolder(
            binding = binding,
            listener = null,
            context = context,
            searchMode = false,
            markwon = mockk(relaxed = true),
            tasksViewPool = RecyclerView.RecycledViewPool(),
            attachmentsViewPool = RecyclerView.RecycledViewPool()
        )

        val note = Note(content = "Old Content", isMarkdownEnabled = false)
        viewHolder.bind(note)
        assertEquals("Old Content", binding.textViewContent.text.toString())

        val updatedNote = note.copy(content = "New Content")
        viewHolder.runPayloads(updatedNote, setOf(NoteRecyclerAdapter.Payload.ContentChanged))
        assertEquals("New Content", binding.textViewContent.text.toString())
    }

    @Test
    fun testNoteViewHolderContentUpdateViaPayload_Markdown() {
        val binding = LayoutNoteBinding.inflate(LayoutInflater.from(context))
        val markwon = mockk<Markwon>(relaxed = true)
        val viewHolder = NoteViewHolder(
            binding = binding,
            listener = null,
            context = context,
            searchMode = false,
            markwon = markwon,
            tasksViewPool = RecyclerView.RecycledViewPool(),
            attachmentsViewPool = RecyclerView.RecycledViewPool()
        )

        val note = Note(content = "Old Content", isMarkdownEnabled = true)
        viewHolder.bind(note)
        // Since markwon.applyTo is an inline extension function, we verify the underlying Markwon calls
        verify { markwon.parse("Old Content") }
        verify { markwon.render(any()) }
        verify { markwon.setParsedMarkdown(binding.textViewContent, any()) }

        val updatedNote = note.copy(content = "New Content")
        viewHolder.runPayloads(updatedNote, setOf(NoteRecyclerAdapter.Payload.ContentChanged))
        verify { markwon.parse("New Content") }
        verify { markwon.render(any()) }
        verify { markwon.setParsedMarkdown(binding.textViewContent, any()) }
    }

    @Test
    fun testAdapterPayloadFlattening() {
        val markwon = mockk<Markwon>(relaxed = true)
        val adapter = NoteRecyclerAdapter(null, markwon)
        val viewHolder = mockk<NoteViewHolder>(relaxed = true)

        val note = Note(id = 1, title = "Title")
        adapter.submitList(listOf(note))

        val payloads = mutableListOf<Any>(
            listOf(NoteRecyclerAdapter.Payload.TitleChanged),
            listOf(NoteRecyclerAdapter.Payload.ContentChanged, NoteRecyclerAdapter.Payload.TitleChanged)
        )

        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify {
            viewHolder.runPayloads(note, withArg {
                assertEquals(2, it.size)
                assertTrue(it.contains(NoteRecyclerAdapter.Payload.TitleChanged))
                assertTrue(it.contains(NoteRecyclerAdapter.Payload.ContentChanged))
            })
        }
    }
}
