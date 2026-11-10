package org.qosp.notes.data.sync.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.preferences.CloudService

class LegacyUriTitlesTest {

    private val driveId = "acc=1;doc=encoded=lYKhA38WkrOK68pu6m0EycqKPfuiVtV74aguUtm_P_zPFneiNg=="
    private val driveUri = "content://com.google.android.apps.docs.storage/document/" +
        "acc%3D1%3Bdoc%3Dencoded%3DlYKhA38WkrOK68pu6m0EycqKPfuiVtV74aguUtm_P_zPFneiNg%3D%3D"
    private val localUri = "content://com.android.externalstorage.documents/tree/primary%3ANotes/" +
        "document/primary%3ANotes%2FShopping%20list.md"

    private fun note(id: Long, title: String, content: String = "") = Note(id = id, title = title, content = content)

    private fun mapping(noteId: Long, uri: String?) = IdMapping(
        localNoteId = noteId,
        remoteNoteId = null,
        provider = CloudService.FILE_STORAGE,
        extras = null,
        isDeletedLocally = false,
        storageUri = uri,
    )

    private fun remote(uri: String, title: String) =
        SyncNote(id = 0, idStr = uri, content = "", title = title, lastModified = 100)

    @Test
    fun `legacy title of a drive uri is the opaque document id`() {
        assertEquals(driveId, legacyTitleFromStorageUri(driveUri))
    }

    @Test
    fun `legacy title of a local storage uri is the file name`() {
        assertEquals("Shopping list", legacyTitleFromStorageUri(localUri))
    }

    @Test
    fun `legacy title of a missing uri is null`() {
        assertNull(legacyTitleFromStorageUri(null))
        assertNull(legacyTitleFromStorageUri("  "))
    }

    @Test
    fun `title equal to the document id is legacy when the file has a real name`() {
        assertTrue(isLegacyUriTitle(driveId, "Meeting notes", driveUri))
    }

    @Test
    fun `local storage titles are never legacy`() {
        assertFalse(isLegacyUriTitle("Shopping list", "Shopping list", localUri))
    }

    @Test
    fun `a genuine title differing from the uri is not legacy`() {
        assertFalse(isLegacyUriTitle("Meeting notes", "Meeting notes", driveUri))
        assertFalse(isLegacyUriTitle("Renamed", "Meeting notes", driveUri))
    }

    @Test
    fun `empty title is not legacy`() {
        assertFalse(isLegacyUriTitle("", "Meeting notes", driveUri))
    }

    @Test
    fun `damaged title is repaired from the real remote file name`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, driveId, "body")),
            remoteNotes = listOf(remote(driveUri, "Meeting notes")),
            mappings = listOf(mapping(1, driveUri)),
        )
        assertEquals(1, fixes.size)
        assertEquals("Meeting notes", fixes[0].newTitle)
        assertFalse(fixes[0].renameRemote)
    }

    @Test
    fun `when the remote file is also named after the id the first line is used and the file renamed`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, driveId, "# Meeting notes\nmore")),
            remoteNotes = listOf(remote(driveUri, driveId)),
            mappings = listOf(mapping(1, driveUri)),
        )
        assertEquals(1, fixes.size)
        assertEquals("Meeting notes", fixes[0].newTitle)
        assertTrue(fixes[0].renameRemote)
    }

    @Test
    fun `healthy notes are left alone`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, "Meeting notes"), note(2, "Shopping list")),
            remoteNotes = listOf(remote(driveUri, "Meeting notes"), remote(localUri, "Shopping list")),
            mappings = listOf(mapping(1, driveUri), mapping(2, localUri)),
        )
        assertTrue(fixes.isEmpty())
    }

    @Test
    fun `notes being overwritten from remote are skipped`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, driveId)),
            remoteNotes = listOf(remote(driveUri, "Meeting notes")),
            mappings = listOf(mapping(1, driveUri)),
            skipNoteIds = setOf(1L),
        )
        assertTrue(fixes.isEmpty())
    }

    @Test
    fun `unmapped notes and missing remote files are ignored`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, driveId), note(2, driveId)),
            remoteNotes = listOf(remote(driveUri, "Meeting notes")),
            mappings = listOf(mapping(2, "content://elsewhere/document/gone")),
        )
        assertTrue(fixes.isEmpty())
    }

    @Test
    fun `a blank remote name is not used as a title`() {
        val fixes = findLegacyTitleFixes(
            localNotes = listOf(note(1, driveId)),
            remoteNotes = listOf(remote(driveUri, " ")),
            mappings = listOf(mapping(1, driveUri)),
        )
        assertTrue(fixes.isEmpty())
    }

    @Test
    fun `fallback title strips markdown markers and falls back to untitled`() {
        assertEquals("Meeting", fallbackTitleFor(note(1, "x", "\n\n## Meeting\nbody")))
        assertEquals("Untitled", fallbackTitleFor(note(1, "x", "")))
        assertEquals(60, fallbackTitleFor(note(1, "x", "a".repeat(200))).length)
    }
}
