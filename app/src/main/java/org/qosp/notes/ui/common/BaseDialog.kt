package org.qosp.notes.ui.common

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

abstract class BaseDialog<V : ViewBinding> : DialogFragment() {
    private var _dialog: AlertDialog? = null
    val dialog get() = _dialog!!

    private var _binding: V? = null
    protected val binding: V get() = _binding ?: throw Exception("Tried to access view binding outside lifecycle")

    abstract fun createBinding(inflater: LayoutInflater): V

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return MaterialAlertDialogBuilder(requireContext(), theme)
            .apply {
                _binding = createBinding(LayoutInflater.from(requireContext()))
                setView(binding.root)
            }
            .create()
            .apply {
                _dialog = this
                onViewCreated(binding.root, savedInstanceState)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _dialog = null
        _binding = null
    }

    companion object {
        inline fun build(context: Context, block: MaterialAlertDialogBuilder.() -> Unit): AlertDialog {
            val builder = MaterialAlertDialogBuilder(context)
            block(builder)
            return builder.create()
        }
    }
}

/**
 * Use when you want to set a dialog button which does not dismiss the dialog when clicked
 */
inline fun AlertDialog.setButton(
    whichButton: Int,
    text: CharSequence?,
    lifecycleOwner: LifecycleOwner,
    crossinline onClick: () -> Unit,
) {
    setButton(whichButton, text, null, null)
    lifecycleOwner.lifecycleScope.launchWhenResumed {
        getButton(whichButton).setOnClickListener {
            onClick()
        }
    }
}
