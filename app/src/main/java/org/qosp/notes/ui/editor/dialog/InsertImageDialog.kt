package org.qosp.notes.ui.editor.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.databinding.DialogInsertImageBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.editor.EditorFragment
import org.qosp.notes.ui.editor.markdown.imageMarkdown
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class InsertImageDialog : BaseDialog<DialogInsertImageBinding>() {

    private var text: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = arguments?.getString(TEXT)
    }

    override fun createBinding(inflater: LayoutInflater) = DialogInsertImageBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editTextImageDescription.setText(text.toString())

        dialog.apply {
            setTitle(getString(R.string.action_insert_image))
            setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_cancel)) { _, _ -> }
            setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_insert)) { _, _ ->
                val markdown = imageMarkdown(
                    url = binding.editTextImagePath.text.toString(),
                    description = binding.editTextImageDescription.text.toString(),
                )
                setFragmentResult(
                    EditorFragment.MARKDOWN_DIALOG_RESULT,
                    bundleOf(
                        EditorFragment.MARKDOWN_DIALOG_RESULT to markdown
                    )
                )
            }
        }

        if (binding.editTextImageDescription.text?.isEmpty() == true) {
            binding.editTextImageDescription.requestFocusAndKeyboard()
        } else {
            binding.editTextImagePath.requestFocusAndKeyboard()
        }
    }

    companion object {
        private const val TEXT = "TEXT"

        fun build(text: String): InsertImageDialog {
            return InsertImageDialog().apply {
                arguments = bundleOf(
                    TEXT to text,
                )
            }
        }
    }
}
