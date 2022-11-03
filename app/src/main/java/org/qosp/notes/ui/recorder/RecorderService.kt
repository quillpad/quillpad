package org.qosp.notes.ui.recorder

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaRecorder
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecorderService : LifecycleService() {
    private var binder: RecorderServiceBinder? = null

    override fun onCreate() {
        super.onCreate()
        binder = RecorderServiceBinder(lifecycleScope, applicationContext)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()

        binder?.killRecorder()
        binder = null
    }

    enum class RecorderState { DEAD, INITIALIZED, RECORDING }

    companion object {
        const val TIMEOUT = 60 * 60 * 2
    }
}

class RecorderServiceBinder(
    private val lifecycleScope: CoroutineScope,
    private val applicationContext: Context,
) : Binder() {
    private var mediaRecorder: MediaRecorder? = null
    private var state = RecorderService.RecorderState.DEAD
    private var totalSeconds = 0L
    private var outputFile: File? = null
    private val _recordingTime: MutableStateFlow<Long> = MutableStateFlow(0)

    var recordingTime: Flow<Long> = _recordingTime
    var outputUri: Uri? = null
    val isRecording get() = state == RecorderService.RecorderState.RECORDING

    fun killRecorder(shouldStopService: Boolean = false) {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        state = RecorderService.RecorderState.DEAD
        totalSeconds = 0L

        if (shouldStopService) {
            applicationContext.stopService(Intent(applicationContext, RecorderService::class.java))
        }
    }

    private fun stopRecording() {
        if (state != RecorderService.RecorderState.RECORDING) return
        killRecorder(true)
    }

    fun initializeRecorder(output: File, uri: Uri) {
        if (mediaRecorder != null) return
        outputUri = uri
        outputFile = output
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(output.path)
        }
        state = RecorderService.RecorderState.INITIALIZED
    }

    fun startRecording() {
        if (state == RecorderService.RecorderState.RECORDING) return
        mediaRecorder?.prepare()
        mediaRecorder?.start()
        state = RecorderService.RecorderState.RECORDING

        lifecycleScope.launch {
            while (true) {
                _recordingTime.emit(totalSeconds++)
                delay(1000)
                if (totalSeconds >= RecorderService.TIMEOUT) stopRecording()
            }
        }
    }

    fun stopRecordingAndUnbind(connection: ServiceConnection) {
        applicationContext.unbindService(connection)
        stopRecording()
    }
}
