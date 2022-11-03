package org.qosp.notes.ui.sync.nextcloud

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.qosp.notes.R
import org.qosp.notes.databinding.DialogNextcloudServerBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.utils.requestFocusAndKeyboard

@AndroidEntryPoint
class NextcloudServerDialog : BaseDialog<DialogNextcloudServerBinding>() {
    private val model: NextcloudViewModel by activityViewModels()
    private var url: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString(URL, "").toString()
    }

    override fun createBinding(inflater: LayoutInflater) = DialogNextcloudServerBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog.setTitle(getString(R.string.preferences_nextcloud_instance_url))
        binding.editTextServerUrl.setText(url)

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this) {
            val url = binding.editTextServerUrl.text?.toString()?.trim() ?: ""
            if (URLUtil.isHttpsUrl(url)) {
                model.setURL(url)
                return@setButton dismiss()
            }
            Toast.makeText(requireContext(), getString(R.string.message_not_valid_https), Toast.LENGTH_SHORT).show()
        }

        binding.editTextServerUrl.requestFocusAndKeyboard()
    }

    companion object {
        private const val URL = "URL"
        fun build(url: String?): NextcloudServerDialog {
            return NextcloudServerDialog().apply {
                arguments = bundleOf(
                    URL to url
                )
            }
        }
    }
}
