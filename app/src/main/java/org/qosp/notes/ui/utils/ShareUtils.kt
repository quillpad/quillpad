package org.qosp.notes.ui.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.attachments.uri

fun shareNote(context: Context, note: Note) {
    val sendIntent: Intent = Intent().apply {
        val textContent = if (note.isList) note.taskListToString(withCheckmarks = true) else note.content
        putExtra(
            Intent.EXTRA_TITLE,
            note.title
        )
        putExtra(
            Intent.EXTRA_TEXT,
            textContent,
        )

        note.attachments
            .map { it.uri(context) }
            .ifEmpty {
                action = Intent.ACTION_SEND
                type = "text/plain"
                null
            }
            ?.let { uris ->
                clipData = ClipData("", arrayOf("*/*"), ClipData.Item(uris.first())).apply {
                    (1 until uris.size).forEach { addItem(ClipData.Item(uris[it])) }
                }

                action = Intent.ACTION_SEND_MULTIPLE
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    val chooser = Intent.createChooser(sendIntent, null)
    ContextCompat.startActivity(context, chooser, null)
}

fun shareAttachment(context: Context, attachment: Attachment) {
    val sendIntent: Intent = Intent().apply {
        val uri = attachment.uri(context) ?: return

        action = Intent.ACTION_SEND
        data = uri
        type = "*/*"

        clipData = ClipData(
            attachment.description,
            arrayOf("*/*"),
            ClipData.Item(uri)
        )

        putExtra(Intent.EXTRA_TEXT, attachment.description)
        putExtra(Intent.EXTRA_STREAM, uri)

        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.action_share))
    ContextCompat.startActivity(context, chooser, null)
}
