package org.qosp.notes.ui.attachments.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.databinding.DialogEditAttachmentBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class EditAttachmentDialog : BaseDialog<DialogEditAttachmentBinding>() {
    private val model: AttachmentDialogViewModel by activityViewModels()

    private var path: String? = null
    private var noteId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = arguments?.getString(ATTACHMENT_PATH)
        noteId = arguments?.getLong(NOTE_ID)
    }

    override fun createBinding(inflater: LayoutInflater) = DialogEditAttachmentBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val noteId = noteId ?: return
        val path = path ?: return

        dialog.apply {
            setTitle(getString(R.string.attachments_edit_description))
            setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save)) { _, _ ->
                model.updateAttachmentDescription(
                    noteId,
                    path,
                    binding.editTextDescription.text.toString()
                )
                dismiss()
            }
        }

        model.getAttachment(noteId, path).collect(this) {
            if (it == null) return@collect
            binding.editTextDescription.setText(it.description)
            if (it.description.isEmpty()) binding.editTextDescription.requestFocusAndKeyboard()
        }
    }

    companion object {
        private const val ATTACHMENT_PATH = "ATTACHMENT_PATH"
        private const val NOTE_ID = "NOTE_ID"

        fun build(noteId: Long, attachmentPath: String): EditAttachmentDialog {
            return EditAttachmentDialog().apply {
                arguments = bundleOf(
                    ATTACHMENT_PATH to attachmentPath,
                    NOTE_ID to noteId
                )
            }
        }
    }
}
