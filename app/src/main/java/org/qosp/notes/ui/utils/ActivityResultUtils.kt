package org.qosp.notes.ui.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import java.time.Instant

fun ActivityResultLauncher<None>.launch() {
    launch(null)
}

object None

object ChooseFilesContract : ActivityResultContract<None, List<Uri>>() {
    override fun createIntent(context: Context, input: None?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "audio/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = "*/*"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (intent == null || resultCode != Activity.RESULT_OK) return emptyList()

        val clipItemCount = intent.clipData?.itemCount ?: 0
        return listOfNotNull(intent.data) + (0 until clipItemCount).mapNotNull {
            intent.clipData?.getItemAt(it)?.uri
        }
    }
}

object ExportNotesContract : ActivityResultContract<None, Uri?>() {
    override fun createIntent(context: Context, input: None?): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "quillnote_backup_${Instant.now().epochSecond}.zip")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent != null && resultCode == Activity.RESULT_OK) intent.data else null
    }
}

object RestoreNotesContract : ActivityResultContract<None, Uri?>() {
    override fun createIntent(context: Context, input: None?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent != null && resultCode == Activity.RESULT_OK) intent.data else null
    }
}

object TakePictureContract : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}
