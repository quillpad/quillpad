package org.qosp.notes.ui.media

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.qosp.notes.App
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.ui.attachments.getAlbumArtBitmap
import org.qosp.notes.ui.attachments.getAttachmentFilename
import org.qosp.notes.ui.attachments.uri
import org.qosp.notes.ui.utils.generateId

class MusicService : LifecycleService() {
    private var binder: MusicServiceBinder? = null

    override fun onCreate() {
        super.onCreate()
        binder = MusicServiceBinder(lifecycleScope, applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action?.let { IntentAction.valueOf(it) }) {
            IntentAction.PLAY -> binder?.startPlaying()
            IntentAction.PAUSE -> binder?.pausePlaying()
            IntentAction.STOP -> binder?.stopPlaying(shouldStopService = true, shouldReleasePlayer = true)
            IntentAction.STOP_SERVICE -> {
                stopForeground(true)
                stopSelf()
            }
            else -> {
                binder?.run {
                    notificationId?.let { startForeground(it, builder.build()) }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()

        binder?.stopPlaying(shouldStopService = false, shouldReleasePlayer = true)
        binder = null
    }

    enum class IntentAction {
        PLAY,
        PAUSE,
        STOP,
        STOP_SERVICE,
        START_FOREGROUND,
    }
}

class MusicServiceBinder(
    private val lifecycleScope: CoroutineScope,
    private val applicationContext: Context,
) : Binder() {

    private var state: State = State.IDLE
    private var source: Uri = Uri.EMPTY
    private var infoJob: Job? = null

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(applicationContext, "PlaybackService")
    private val notificationManager = applicationContext.getSystemService<NotificationManager>()
    private val _playbackInfo: MutableStateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())

    val playbackInfo: Flow<PlaybackInfo> get() = _playbackInfo
    val builder: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, App.PLAYBACK_CHANNEL_ID)
    var notificationId: Int? = null

    private var pausedByUser: Boolean = true

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_GAIN -> if (state != State.COMPLETED && !pausedByUser) startPlaying()
            else -> pausePlaying(byUser = false)
        }
    }

    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).run {
        setAudioAttributes(
            AudioAttributesCompat.Builder().run {
                setUsage(AudioAttributesCompat.USAGE_MEDIA)
                setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                build()
            }
        )

        setOnAudioFocusChangeListener(audioFocusListener)
        build()
    }

    private fun releasePlayer() {
        mediaPlayer.release()
        mediaSession.release()
    }

    private fun startEmittingPlaybackInfo() {
        infoJob = lifecycleScope.launch {
            while (true) {
                delay(250L)
                _playbackInfo.emit(
                    PlaybackInfo(
                        duration = mediaPlayer.duration,
                        position = mediaPlayer.currentPosition,
                        isPlaying = if (state.atLeast(State.INITIALIZED)) mediaPlayer.isPlaying else false,
                        isReleased = false,
                    )
                )
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setState(newState: State) {
        fun updatePlayPauseButton(action: MusicService.IntentAction) {
            builder.mActions[0] = NotificationCompat.Action(
                if (action == MusicService.IntentAction.PLAY) R.drawable.ic_play else R.drawable.ic_pause,
                applicationContext.getString(R.string.notification_media_play_pause),
                getPendingIntentForAction(action)
            )
            notificationId?.let { notificationManager?.notify(it, builder.build()) }
        }

        fun buildPlaybackState(state: Int) = PlaybackStateCompat.Builder()
            .setState(state, mediaPlayer.currentPosition.toLong(), 1F)
            .build()

        state = newState
        pausedByUser = if (newState != State.PAUSED) true else pausedByUser

        val playbackState = when (newState) {
            State.INITIALIZED -> buildPlaybackState(PlaybackState.STATE_NONE)
            State.IDLE -> buildPlaybackState(PlaybackState.STATE_NONE)
            State.PREPARING -> buildPlaybackState(PlaybackState.STATE_NONE)
            State.PREPARED -> buildPlaybackState(PlaybackState.STATE_NONE)
            State.STARTED -> {
                updatePlayPauseButton(MusicService.IntentAction.PAUSE)
                buildPlaybackState(PlaybackState.STATE_PLAYING)
            }
            State.PAUSED -> {
                updatePlayPauseButton(MusicService.IntentAction.PLAY)
                buildPlaybackState(PlaybackState.STATE_PAUSED)
            }
            State.COMPLETED -> {
                updatePlayPauseButton(MusicService.IntentAction.PLAY)
                buildPlaybackState(PlaybackState.STATE_STOPPED)
            }
        }

        mediaSession.setPlaybackState(playbackState)
    }

    private fun getIntentForAction(
        action: MusicService.IntentAction,
        extras: List<Pair<String, Any>> = listOf(),
    ): Intent {
        return Intent(applicationContext, MusicService::class.java).apply {
            extras.forEach { (name, value) -> if (value is Parcelable) putExtra(name, value) }
            this.action = action.name
        }
    }

    private fun getPendingIntentForAction(
        action: MusicService.IntentAction,
        extras: List<Pair<String, Any>> = listOf()
    ): PendingIntent {
        val defaultFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getService(
            applicationContext, 0,
            getIntentForAction(action, extras),
            defaultFlag,
        )
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = applicationContext.getSystemService<AudioManager>() ?: return false
        val res = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun initializeNotificationBuilder(attachment: Attachment, uri: Uri) {
        builder.apply {
            setContentText(attachment.description)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setSmallIcon(R.drawable.ic_notification_icon)
            setOnlyAlertOnce(true)
            setSilent(true)
            clearActions()

            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    applicationContext.getString(R.string.notification_media_play_pause),
                    getPendingIntentForAction(MusicService.IntentAction.PAUSE)
                )
            )

            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_close,
                    applicationContext.getString(R.string.notification_media_stop),
                    getPendingIntentForAction(MusicService.IntentAction.STOP)
                )
            )

            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )

            // Fetch filename and set it as notification title
            getAttachmentFilename(applicationContext, uri)?.let {
                setContentTitle(it)
            }

            // Fetch album art and set is as the large icon, if it exists
            getAlbumArtBitmap(applicationContext, uri)?.let { setLargeIcon(it) }
        }
    }

    fun initializePlayer(attachment: Attachment) {
        val attachmentUri = attachment.uri(applicationContext) ?: return
        source = if (source != attachmentUri) attachmentUri else return

        stopPlaying(shouldStopService = false, shouldReleasePlayer = false)

        if (state.atLeast(State.INITIALIZED)) {
            mediaPlayer.reset()
            setState(State.IDLE)
        }

        mediaPlayer.apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener {
                setState(State.PREPARED)
                mediaSession.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.duration.toLong())
                        .build()
                )
                startEmittingPlaybackInfo()
                startPlaying()
            }
            setOnCompletionListener {
                if (!isLooping) setState(State.COMPLETED)
            }
            setDataSource(applicationContext, source)
            setState(State.INITIALIZED)
            prepareAsync()
            setState(State.PREPARING)
        }

        notificationId = notificationId ?: notificationManager?.generateId() ?: return

        initializeNotificationBuilder(attachment, attachmentUri)
        applicationContext.startService(
            getIntentForAction(MusicService.IntentAction.START_FOREGROUND)
        )
    }

    fun startPlaying() {
        if (requestAudioFocus() && state.atLeast(State.PREPARED)) {
            mediaPlayer.start()
            setState(State.STARTED)
        }
    }

    fun pausePlaying(byUser: Boolean = true) {
        if (state == State.STARTED) {
            mediaPlayer.pause()
            setState(State.PAUSED)
            pausedByUser = byUser
        }
    }

    fun stopPlaying(shouldStopService: Boolean, shouldReleasePlayer: Boolean) {
        infoJob?.cancel()

        if (state in setOf(State.STARTED, State.PAUSED, State.COMPLETED)) {
            mediaPlayer.stop()
            setState(State.IDLE)
            mediaPlayer.reset()
        }

        if (shouldReleasePlayer) {
            releasePlayer()
            lifecycleScope.launch {
                _playbackInfo.emit(
                    PlaybackInfo(isReleased = true)
                )
            }
        }

        if (shouldStopService) {
            applicationContext.startService(
                getIntentForAction(
                    action = MusicService.IntentAction.STOP_SERVICE,
                )
            )
        }
    }

    fun seekTo(percentage: Float) {
        if (!state.atLeast(State.INITIALIZED)) return
        mediaPlayer.seekTo((mediaPlayer.duration * percentage).toInt())
        setState(state)
    }

    private enum class State(val value: Int) {
        IDLE(0),
        INITIALIZED(1),
        PREPARING(2),
        PREPARED(3),
        STARTED(4),
        PAUSED(5),
        COMPLETED(6);

        fun atLeast(state: State): Boolean = value >= state.value
    }
}
