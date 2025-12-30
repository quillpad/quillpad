package org.qosp.notes.components.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `restore should fail on too many entries`() = runBlocking {
        val tmpFile = File.createTempFile("test", ".zip")
        ZipOutputStream(FileOutputStream(tmpFile)).use { out ->
            for (i in 0..2000) { // large number to exceed limits
                out.putNextEntry(ZipEntry("file_$i.txt"))
                out.write("content".toByteArray())
                out.closeEntry()
            }
        }

        val manager = BackupManager(
            currentVersion = 1,
            noteRepository = TODO(),
            notebookRepository = TODO(),
            tagRepository = TODO(),
            reminderRepository = TODO(),
            idMappingRepository = TODO(),
            reminderManager = TODO(),
            context = context
        )

        assertThrows(java.io.IOException::class.java) {
            manager.backupFromZipFile(tmpFile.toURI().toString().let { android.net.Uri.parse(it) }, TODO())
        }
    }
}
