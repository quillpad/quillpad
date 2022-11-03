package org.qosp.notes.ui.notebooks.dialog

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.databinding.DialogEditNotebookBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class EditNotebookDialog : BaseDialog<DialogEditNotebookBinding>() {
    private val model: NotebookDialogViewModel by activityViewModels()
    private lateinit var notebook: Notebook

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notebook = arguments?.getParcelable(NOTEBOOK) ?: return
    }

    override fun createBinding(inflater: LayoutInflater) = DialogEditNotebookBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when {
            this::notebook.isInitialized -> {
                // Valid notebook id
                dialog.setTitle(getString(R.string.action_rename_notebook))
                binding.editTextNotebookName.setText(notebook.name)
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
                    val name = binding.editTextNotebookName.text
                        .toString()
                        .ifEmpty { getString(R.string.indicator_untitled) }

                    lifecycleScope.launch {
                        val exists = model.notebookExistsByName(name, ignoreId = notebook.id)

                        if (!exists) {
                            val notebook = notebook.copy(name = name)
                            model.updateNotebook(notebook)
                            return@launch dismiss()
                        }

                        Toast
                            .makeText(requireContext(), getString(R.string.indicator_notebook_already_exists, name), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                binding.editTextNotebookName.requestFocusAndKeyboard()
            }
            else -> {
                dialog.setTitle(getString(R.string.action_new_notebook))
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
                    val name = binding.editTextNotebookName.text
                        .toString()
                        .ifEmpty { getString(R.string.indicator_untitled) }

                    lifecycleScope.launch {
                        val exists = model.notebookExistsByName(name)

                        if (!exists) {
                            val tag = Notebook(name)
                            model.insertNotebook(tag)
                            return@launch dismiss()
                        }

                        Toast
                            .makeText(requireContext(), getString(R.string.indicator_notebook_already_exists, name), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                binding.editTextNotebookName.requestFocusAndKeyboard()
            }
        }
    }

    companion object {
        private const val NOTEBOOK = "NOTEBOOK"
        fun build(notebook: Notebook?): EditNotebookDialog {
            return EditNotebookDialog().apply {
                arguments = if (notebook == null) bundleOf() else bundleOf(
                    NOTEBOOK to notebook
                )
            }
        }
    }
}
