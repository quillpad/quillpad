package org.qosp.notes.ui.tags.dialog

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
import org.qosp.notes.data.model.Tag
import org.qosp.notes.databinding.DialogEditTagBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class EditTagDialog : BaseDialog<DialogEditTagBinding>() {
    private val model: TagDialogViewModel by activityViewModels()
    private lateinit var tag: Tag

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tag = arguments?.getParcelable(TAG) ?: return
    }

    override fun createBinding(inflater: LayoutInflater) = DialogEditTagBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when {
            this::tag.isInitialized -> {
                dialog.setTitle(getString(R.string.action_rename_tag))
                binding.editTextTagName.setText(tag.name)
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
                    val name = binding.editTextTagName.text
                        .toString()
                        .ifEmpty { getString(R.string.indicator_untitled) }

                    lifecycleScope.launch {
                        val exists = model.tagExistsByName(name, ignoreId = tag.id)

                        if (!exists) {
                            val tag = tag.copy(name = name)
                            model.updateTag(tag)
                            return@launch dismiss()
                        }

                        Toast
                            .makeText(requireContext(), getString(R.string.indicator_tag_already_exists, name), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                binding.editTextTagName.requestFocusAndKeyboard()
            }
            else -> {
                dialog.setTitle(getString(R.string.action_new_tag))
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
                    val name = binding.editTextTagName.text
                        .toString()
                        .ifEmpty { getString(R.string.indicator_untitled) }

                    lifecycleScope.launch {
                        val exists = model.tagExistsByName(name)

                        if (!exists) {
                            val tag = Tag(name)
                            model.insertTag(tag)
                            return@launch dismiss()
                        }

                        Toast
                            .makeText(requireContext(), getString(R.string.indicator_tag_already_exists, name), Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                binding.editTextTagName.requestFocusAndKeyboard()
            }
        }
    }

    companion object {
        private const val TAG = "TAG"

        fun build(tag: Tag?): EditTagDialog {
            return EditTagDialog().apply {
                arguments = bundleOf(
                    TAG to tag
                )
            }
        }
    }
}
