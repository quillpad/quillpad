package org.qosp.notes.components.backup

import android.content.Context
import android.net.Uri
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.ui.attachments.getAttachmentFilename
import kotlin.collections.set

interface ProgressHandler {
    fun onProgressChanged(current: Int, max: Int)
    fun onCompletion()
    fun onFailure(e: Throwable)
}

interface MigrationHandler {
    fun migrate(deserialized: String): String
}

class DefaultMigrationHandler : MigrationHandler {
    override fun migrate(deserialized: String): String {
        return deserialized
    }
}

sealed interface AttachmentHandler {
    fun handle(old: Attachment): Attachment?

    class IncludeFiles(private val context: Context) : AttachmentHandler {
        /***
         * Map of filenames to their according content URI.
         */
        val attachmentsMap = mutableMapOf<String, Uri>()
        private val count = mutableMapOf<String, Int>()

        override fun handle(old: Attachment): Attachment? {
            val uri = Uri.parse(old.path)
            val existingFile = attachmentsMap
                .filterValues { it == uri }
                .map { it.key }
                .firstOrNull()

            if (existingFile == null) {
                var fileName = getAttachmentFilename(context, uri) ?: return null

                when {
                    !attachmentsMap.containsKey(fileName) -> count[fileName] = 0
                    else -> {
                        val id = (count[fileName] ?: 0) + 1
                        count[fileName] = id
                        fileName = "${id}_$fileName"
                    }
                }
                attachmentsMap[fileName] = uri

                return old.copy(fileName = fileName, path = "")
            }

            return old.copy(fileName = existingFile, path = "")
        }
    }

    object KeepInfoOnly : AttachmentHandler {
        override fun handle(old: Attachment) = old
    }

    object KeepNothing : AttachmentHandler {
        override fun handle(old: Attachment): Attachment? = null
    }
}
