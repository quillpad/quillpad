package org.qosp.notes.data

import org.junit.Assert.*
import org.junit.Test
import org.qosp.notes.data.model.*
import org.qosp.notes.preferences.CloudService

class BackupTest {

    @Test
    fun serializer() {
        val backup = Backup(
            1,
            setOf(
                Note(
                    title = "title",
                    content = "content",
                    isList = false,
                    taskList = listOf(NoteTask(33L, "reminder", false)),
                    attachments = listOf(Attachment(type = Attachment.Type.AUDIO, path = "sdt")),
                    tags = listOf(Tag("tag", 33L)),
                    reminders = listOf(Reminder("reminder", 2442L, 234)),
                    color = NoteColor.Blue
                )
            ),
            setOf(Notebook("nb")),
            joins = setOf(NoteTagJoin(33, 44)),
            idMappings = setOf(
                IdMapping(
                    33, 44, 12,
                    CloudService.NEXTCLOUD, null, false, false
                )
            )
        )
        assertNotNull(backup.serialize())
    }
}