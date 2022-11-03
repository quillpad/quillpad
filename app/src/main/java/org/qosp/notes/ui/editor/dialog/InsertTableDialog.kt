package org.qosp.notes.ui.editor.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.databinding.DialogInsertTableBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.editor.EditorFragment
import org.qosp.notes.ui.editor.markdown.tableMarkdown
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class InsertTableDialog : BaseDialog<DialogInsertTableBinding>() {
    override fun createBinding(inflater: LayoutInflater) = DialogInsertTableBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog.apply {
            setTitle(getString(R.string.action_insert_table))
            setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_cancel)) { _, _ -> }
            setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_insert), this@InsertTableDialog) {
                val rows = binding.editTextRows.text.toString()
                val columns = binding.editTextColumns.text.toString()

                if (rows.isBlank() || columns.isBlank() || !rows.isDigitsOnly() || !columns.isDigitsOnly()) {
                    Toast.makeText(requireContext(), getString(R.string.message_invalid_number_rows_columns), Toast.LENGTH_SHORT).show()
                    return@setButton
                }

                val markdown = tableMarkdown(
                    rows = rows.toInt(),
                    columns = columns.toInt(),
                )
                setFragmentResult(
                    EditorFragment.MARKDOWN_DIALOG_RESULT,
                    bundleOf(
                        EditorFragment.MARKDOWN_DIALOG_RESULT to markdown
                    )
                )
                dismiss()
            }
        }

        if (binding.editTextColumns.text?.isEmpty() == true) {
            binding.editTextColumns.requestFocusAndKeyboard()
        } else {
            binding.editTextRows.requestFocusAndKeyboard()
        }
    }
}
