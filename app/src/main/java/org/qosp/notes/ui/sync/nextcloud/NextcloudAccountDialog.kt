package org.qosp.notes.ui.sync.nextcloud

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.sync.core.NoConnectivity
import org.qosp.notes.data.sync.core.ServerNotSupported
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.data.sync.core.Unauthorized
import org.qosp.notes.databinding.DialogNextcloudAccountBinding
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.common.setButton
import org.qosp.notes.ui.utils.requestFocusAndKeyboard
import javax.inject.Inject

@AndroidEntryPoint
class NextcloudAccountDialog : BaseDialog<DialogNextcloudAccountBinding>() {
    private val model: NextcloudViewModel by activityViewModels()

    private var username = ""
    private var password = ""

    @Inject
    lateinit var syncManager: SyncManager

    override fun createBinding(inflater: LayoutInflater) = DialogNextcloudAccountBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog.setTitle(getString(R.string.preferences_nextcloud_account))

        lifecycleScope.launch {
            username = model.username.first()
            password = model.password.first()

            if (username.isNotBlank() && password.isNotBlank()) {
                binding.editTextUsername.setText(username)
                binding.editTextPassword.setText(password)
            }
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), this@NextcloudAccountDialog) {
            username = binding.editTextUsername.text.toString()
            password = binding.editTextPassword.text.toString()

            if (username.isBlank() or password.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.message_credentials_cannot_be_blank), Toast.LENGTH_SHORT).show()
                return@setButton
            }

            Toast.makeText(requireContext(), getString(R.string.indicator_connecting), Toast.LENGTH_LONG).show()

            lifecycleScope.launch {
                val result = model.authenticate(username, password)
                val messageResId = when (result) {
                    NoConnectivity -> R.string.message_internet_not_available
                    ServerNotSupported -> R.string.message_server_not_compatible
                    Success -> R.string.message_logged_in_successfully
                    Unauthorized -> R.string.message_invalid_credentials
                    else -> R.string.message_something_went_wrong
                }
                Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT).show()
                if (result == Success) dismiss()
            }
        }

        binding.editTextUsername.requestFocusAndKeyboard()
    }
}
