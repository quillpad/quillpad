package org.qosp.notes.ui.recorder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.databinding.DialogRecordAudioBinding
import org.qosp.notes.ui.attachments.fromUri
import org.qosp.notes.ui.common.BaseDialog
import org.qosp.notes.ui.utils.collect
import javax.inject.Inject

const val RECORD_CODE = "RECORD"
const val RECORDED_ATTACHMENT = "RECORDED_ATTACHMENT"

@AndroidEntryPoint
class RecordAudioDialog() : BaseDialog<DialogRecordAudioBinding>() {
    private var recorderService: RecorderServiceBinder? = null
    private var isPermissionGranted = false

    @Inject
    lateinit var mediaStorageManager: MediaStorageManager

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            recorderService = service as RecorderServiceBinder
            lifecycleScope.launch {
                mediaStorageManager.createMediaFile(MediaStorageManager.MediaType.AUDIO)?.let { (uri, file) ->
                    recorderService?.initializeRecorder(file, uri)
                    startRecording()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recorderService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onPermissionGranted() else dismiss()
    }

    override fun createBinding(inflater: LayoutInflater) = DialogRecordAudioBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog.setTitle(R.string.action_record_audio)
        startRecording()

        binding.buttonRecord.setOnClickListener {
            if (recorderService?.isRecording == false) {
                // Start recording
                recorderService?.startRecording()
                startRecording()
            } else {
                stopRecording()
            }
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.action_cancel)) { _, _ ->
            if (recorderService?.isRecording == true) {
                recorderService?.stopRecordingAndUnbind(connection)
                recorderService = null
            }
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        requestMicrophonePermission()
        if (!isPermissionGranted) dialog.hide()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (recorderService != null) context?.applicationContext?.unbindService(connection)
        recorderService = null
    }

    private fun startRecording() {
        if (recorderService?.isRecording == true) {
            binding.buttonRecord.setImageResource(R.drawable.ic_stop)
            dialog.setCanceledOnTouchOutside(false)

            recorderService?.recordingTime?.collect(this) { time ->
                val hours = time.div(3600)
                val minutes = ((time / 60) % 60).let { if (it < 10) "0$it" else "$it" }
                val seconds = (time % 60).let { if (it < 10) "0$it" else "$it" }

                dialog.setTitle(
                    requireContext().getString(R.string.indicator_recording, "$hours:$minutes:$seconds")
                )

                if (time >= RecorderService.TIMEOUT) stopRecording()
            }
        }
    }

    private fun stopRecording() {
        val uri = recorderService?.outputUri ?: Uri.EMPTY

        recorderService?.stopRecordingAndUnbind(connection)
        recorderService = null

        setFragmentResult(
            RECORD_CODE,
            bundleOf(
                RECORDED_ATTACHMENT to Attachment.fromUri(requireContext(), uri)
                    .copy(description = getString(R.string.indicator_recorded_clip))
            )
        )
        dismiss()
    }

    private fun bindRecorderService() {
        context?.applicationContext?.let { app ->
            Intent(app, RecorderService::class.java).also { intent ->
                app.startService(intent)
                app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    private fun onPermissionGranted() {
        isPermissionGranted = true
        bindRecorderService()
        dialog.show()
    }

    private fun requestMicrophonePermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat
            .checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(permission)
        }
    }
}
