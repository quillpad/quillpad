package org.qosp.notes.data.sync.core

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.sync.neu.NoteAction
import org.qosp.notes.data.sync.neu.RemoteNoteMetaData
import org.qosp.notes.data.sync.neu.SynchronizeNotes
import org.qosp.notes.preferences.CloudService

class SynchronizeNotesTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var idMappingRepository: IdMappingRepository

    @InjectMockKs
    private lateinit var synchronizeNotes: SynchronizeNotes

    @Test
    fun `empty local and remote notes returns empty result`() = runTest {
        // Given
        val localNotes = emptyList<Note>()
        val remoteNotes = emptyList<RemoteNoteMetaData>()
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        coVerify { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) }
    }

    @Test
    fun `local notes without mapping should be created remotely`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val localNotes = listOf(localNote)
        val remoteNotes = emptyList<RemoteNoteMetaData>()
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Create
        assertEquals(localNote, action.note)
        assertEquals("", action.remoteNoteMetaData.id)
        assertEquals(localNote.title, action.remoteNoteMetaData.title)
        assertEquals(localNote.modifiedDate, action.remoteNoteMetaData.lastModified)
    }

    @Test
    fun `remote notes without mapping should be created locally`() = runTest {
        // Given
        val remoteNote = RemoteNoteMetaData(id = "remote1", title = "Remote Note", lastModified = 100L)
        val localNotes = emptyList<Note>()
        val remoteNotes = listOf(remoteNote)
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Create
        assertEquals(remoteNote.title, action.note.title)
        assertEquals(remoteNote.lastModified, action.note.modifiedDate)
        assertEquals(remoteNote, action.remoteNoteMetaData)
    }

    @Test
    fun `local note newer than remote note should update remote`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 200L)
        val remoteNote = RemoteNoteMetaData(id = "2", title = "Remote Note", lastModified = 100L)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNoteMetaData)
    }

    @Test
    fun `remote note newer than local note should update local`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val remoteNote = RemoteNoteMetaData(id = "2", title = "Remote Note", lastModified = 200L)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNoteMetaData)
    }

    @Test
    fun `deleted local note should delete remote note`() = runTest {
        // Given
        val remoteNote = RemoteNoteMetaData(id = "2", title = "Remote Note", lastModified = 100L)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(emptyList(), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Delete
        assertEquals(1L, action.note.id)
        assertEquals(remoteNote, action.remoteNoteMetaData)
    }

    @Test
    fun `deleted remote note should delete local note mapping`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), emptyList(), CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Create
        assertEquals(localNote, action.note)
        assertEquals("", action.remoteNoteMetaData.id)
    }

    @Test
    fun `file storage service should use storageUri instead of remoteNoteId`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 200L)
        val remoteNote = RemoteNoteMetaData(id = "file://path/to/note.txt", title = "Remote Note", lastModified = 100L)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = null,
            provider = CloudService.FILE_STORAGE,
            extras = null,
            isDeletedLocally = false,
            storageUri = "file://path/to/note.txt"
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.FILE_STORAGE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNoteMetaData)
    }
}
