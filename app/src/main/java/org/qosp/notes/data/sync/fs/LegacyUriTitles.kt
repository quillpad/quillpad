package org.qosp.notes.data.sync.fs

import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.SyncNote
import java.net.URLDecoder

/*
 * Versions up to 1.5.15 took a note's title from the last path segment of its document URI. That is
 * the file name for the local storage provider, but an opaque document id for providers such as
 * Google Drive (`acc=1;doc=encoded=...`). Notes synced through those providers could end up with the
 * id as their title. The helpers here recognise and repair that damage.
 */

/** The title the old code derived from [storageUri], or null when it cannot be derived. */
internal fun legacyTitleFromStorageUri(storageUri: String?): String? {
    if (storageUri.isNullOrBlank()) return null
    val encodedSegment = storageUri.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
    val segment = runCatching { URLDecoder.decode(encodedSegment.replace("+", "%2B"), "UTF-8") }.getOrNull()
        ?: return null
    return StorageBackend.titleFromFileName(segment.substringAfterLast('/'))
}

/**
 * True when [title] is the old URI-derived title and differs from the real [displayTitle] of the
 * file. For providers where the URI ends in the file name the two coincide, so this is false.
 */
internal fun isLegacyUriTitle(title: String, displayTitle: String, storageUri: String?): Boolean =
    title.isNotEmpty() && title != displayTitle && title == legacyTitleFromStorageUri(storageUri)

// Google Drive document ids, e.g. "acc=1;doc=encoded=lYKhA38W...". Used only when the real file
// name is lost too and the URI alone cannot tell the id apart from a genuine title.
private val driveDocumentId = Regex("^acc=\\d+;doc=")

internal fun looksLikeDriveDocumentId(title: String): Boolean = driveDocumentId.containsMatchIn(title)

internal fun fallbackTitleFor(note: Note): String {
    val firstLine = note.content.lineSequence()
        .map { it.trim().trimStart('#', '-', '*', '>', ' ') }
        .firstOrNull { it.isNotBlank() }
    return firstLine?.take(60)?.trim().takeUnless { it.isNullOrBlank() } ?: "Untitled"
}

internal data class LegacyTitleFix(val note: Note, val newTitle: String, val renameRemote: Boolean)

/**
 * Finds mapped notes whose title was damaged by the old behaviour.
 *
 * - The remote file still has its real name: take the title from it.
 * - The remote file was renamed to the id as well, so the original title is gone: fall back to the
 *   first line of the note and rename the remote file to match.
 *
 * Notes in [skipNoteIds] (those already being overwritten from remote) are left alone.
 */
internal fun findLegacyTitleFixes(
    localNotes: List<Note>,
    remoteNotes: List<SyncNote>,
    mappings: List<IdMapping>,
    skipNoteIds: Set<Long> = emptySet(),
): List<LegacyTitleFix> {
    val localById = localNotes.associateBy { it.id }
    val remoteByUri = remoteNotes.associateBy { it.idStr }
    return mappings.mapNotNull { mapping ->
        val storageUri = mapping.storageUri ?: return@mapNotNull null
        val note = localById[mapping.localNoteId]?.takeUnless { it.id in skipNoteIds } ?: return@mapNotNull null
        val remote = remoteByUri[storageUri] ?: return@mapNotNull null
        when {
            remote.title.isNotBlank() && isLegacyUriTitle(note.title, remote.title, storageUri) ->
                LegacyTitleFix(note, remote.title, renameRemote = false)

            note.title == remote.title && looksLikeDriveDocumentId(note.title) ->
                LegacyTitleFix(note, fallbackTitleFor(note), renameRemote = true)

            else -> null
        }
    }
}
