package org.qosp.notes.tests

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import java.io.IOException
import javax.inject.Inject

@HiltAndroidTest
class MediaStorageManagerTest {
    @Inject
    lateinit var noteRepository: NoteRepository
    @Inject
    lateinit var mediaStorageManager: MediaStorageManager

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    @Throws(Exception::class)
    fun cleanUpStorageTest() = runBlocking {
        // Create the files & Attachment objects
        val attachments = (0 until 4).mapNotNull {
            val (uri, _) = mediaStorageManager.createMediaFile(MediaStorageManager.MediaType.IMAGE) ?: return@runBlocking fail("Could not create file $it")
            Attachment(path = uri.toString())
        }

        assertTrue("Files were not created correctly.", mediaStorageManager.listMediaFiles().size == attachments.size)

        // Create and persist the notes
        val notes = listOf(
            Note(attachments = listOf(attachments[0])),
            Note(attachments = listOf(attachments[0], attachments[2], attachments[3])),
            Note(attachments = listOf(attachments[1])),
        )
            .map { note ->
                val id = noteRepository.insertNote(note)
                note.copy(id = id)
            }

        noteRepository.deleteNotes(notes[1])
        mediaStorageManager.cleanUpStorage()

        var actual = mediaStorageManager.listMediaFiles().size
        var expected = attachments.size - (notes[1].attachments - notes[0].attachments - notes[2].attachments).size

        mediaStorageManager.cleanUpStorage() // Should delete two files
        assertTrue(
            "Deleted note's attachments were not cleaned up properly.",
            actual == expected
        )

        noteRepository.deleteNotes(*notes.toTypedArray())
        mediaStorageManager.cleanUpStorage()

        actual = mediaStorageManager.listMediaFiles().size
        expected = 0
        mediaStorageManager.cleanUpStorage() // Should delete all
        assertTrue(
            "After deleting all notes no media files should exist",
            actual == expected
        )
    }

    @After
    @Throws(IOException::class)
    fun cleanUp() {
        mediaStorageManager.deleteAllMedia()
    }
}
