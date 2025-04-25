package org.qosp.notes.ui.media

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import coil.load
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.util.Util
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.databinding.ActivityMediaBinding
import org.qosp.notes.ui.BaseActivity
import org.qosp.notes.ui.attachments.getAlbumArtBitmap
import org.qosp.notes.ui.attachments.uri
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.getDrawableCompat

@AndroidEntryPoint
class MediaActivity : BaseActivity() {
    private lateinit var binding: ActivityMediaBinding

    private lateinit var attachment: Attachment
    private var backgroundColor = Color.BLACK

    private var exoPlayer: ExoPlayer? = null
    private var playWhenReady = true
    private var playbackPosition: Long = 0

    private var musicService: MusicServiceBinder? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is MusicServiceBinder) return
            musicService = service

            when (attachment.type) {
                Attachment.Type.AUDIO -> {
                    musicService?.initializePlayer(attachment)
                    collectMusicServiceFlow()
                }
                else -> {
                    musicService?.stopPlaying(shouldStopService = true, shouldReleasePlayer = true)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        attachment = intent.extras?.getParcelable(ATTACHMENT) ?: return

        window.setBackgroundDrawable(backgroundColor.toDrawable())

        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            binding.toolbar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            WindowInsetsCompat.CONSUMED
        }
        setupToolbar()

        when (attachment.type) {
            Attachment.Type.AUDIO -> handleAudioAttachment()
            Attachment.Type.IMAGE -> handleImageAttachment()
            Attachment.Type.VIDEO -> {
                /* We initialize the video player in onStart() / onResume() */
                savedInstanceState?.run {
                    playbackPosition = getLong(VIDEO_POSITION)
                    playWhenReady = getBoolean(VIDEO_PLAY_WHEN_READY)
                }
            }
            Attachment.Type.GENERIC -> finish()
        }
    }

    override fun onStart() {
        super.onStart()

        if (attachment.type == Attachment.Type.VIDEO && Util.SDK_INT >= 24) {
            handleVideoAttachment()
        }
    }

    override fun onResume() {
        super.onResume()

        if (attachment.type == Attachment.Type.VIDEO && (Util.SDK_INT < 24 || exoPlayer == null)) {
            handleVideoAttachment()
        }
    }

    override fun onPause() {
        super.onPause()
        if (attachment.type == Attachment.Type.VIDEO && Util.SDK_INT < 24) {
            playWhenReady = exoPlayer?.playWhenReady ?: true
            playbackPosition = exoPlayer?.currentPosition ?: 0
            releaseExoPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (attachment.type == Attachment.Type.VIDEO && Util.SDK_INT >= 24) {
            playWhenReady = exoPlayer?.playWhenReady ?: true
            playbackPosition = exoPlayer?.currentPosition ?: 0
            releaseExoPlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (attachment.type == Attachment.Type.VIDEO) {
            outState.putBoolean(VIDEO_PLAY_WHEN_READY, playWhenReady)
            outState.putLong(VIDEO_POSITION, playbackPosition)
        }
    }

    override fun onDestroy() {
        releaseExoPlayer()
        super.onDestroy()
        if (musicService != null) unbindService(connection)
        musicService = null
    }

    private fun setupToolbar() = with(binding) {
        toolbar.title = attachment.description
        toolbar.setNavigationOnClickListener {
            finish()
        }
        toolbar.navigationIcon?.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(Color.WHITE, BlendModeCompat.SRC_ATOP)
    }

    private fun bindToMusicService(foreground: Boolean) {
        Intent(this, MusicService::class.java).also { intent ->
            if (foreground) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun collectMusicServiceFlow() = with(binding) {
        musicService?.playbackInfo?.collect(this@MediaActivity) { info ->

            if (info.isReleased) {
                finish()
            }

            seekBar.value = info.position.toFloat() / info.duration.toFloat()

            buttonPlayPause.setImageDrawable(
                getDrawableCompat(
                    if (info.isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                )
            )
            buttonPlayPause.setOnClickListener {
                musicService?.let { if (info.isPlaying) it.pausePlaying() else it.startPlaying() }
            }
        }

        // Setup seek bar
        seekBar.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) musicService?.seekTo(value)
        }
    }

    private fun handleAudioAttachment() = with(binding) {
        bindToMusicService(foreground = true)

        toolbar.isVisible = true
        imageView.isVisible = true
        imageView.isZoomable = false
        layoutMediaControls.isVisible = true

        seekBar.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        seekBar.trackActiveTintList = ColorStateList.valueOf(Color.WHITE)
        seekBar.trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 38))

        val attachmentUri = attachment.uri(this@MediaActivity) ?: return@with
        val bitmap = getAlbumArtBitmap(this@MediaActivity, attachmentUri)

        if (bitmap != null) {
            val palette = Palette.from(bitmap)
                .generate()
            val dominant = palette.getDominantColor(backgroundColor)

            imageView.load(bitmap)
            root.background = dominant.toDrawable()
        } else {
            imageView.setColorFilter(Color.WHITE)
            imageView.load(R.drawable.ic_music)
        }
    }

    private fun handleImageAttachment() = with(binding) {
        imageView.isVisible = true
        imageView.load(attachment.path)

        var toolbarHideJob: Job? = null
        imageView.setOnViewTapListener { view, x, y ->
            toolbarHideJob?.cancel()
            toolbarHideJob = lifecycleScope.launch {
                toolbar.isVisible = true
                delay(3000L)
                toolbar.isVisible = false
            }
        }
    }

    private fun handleVideoAttachment() = with(binding) {
        // Bind to the music service so we can stop it if it's playing
        bindToMusicService(foreground = false)

        exoPlayer = ExoPlayer.Builder(this@MediaActivity).build()
        videoView.player = exoPlayer
        videoView.setControllerVisibilityListener {
            toolbar.isVisible = it == PlayerControlView.VISIBLE
        }
        val mediaItem = MediaItem.fromUri(attachment.uri(this@MediaActivity) ?: return@with)
        exoPlayer?.setMediaItem(mediaItem)

        exoPlayer?.playWhenReady = playWhenReady
        exoPlayer?.seekTo(playbackPosition)
        exoPlayer?.prepare()

        videoView.apply {
            isVisible = true
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun releaseExoPlayer() {
        playWhenReady = exoPlayer?.playWhenReady ?: true
        playbackPosition = exoPlayer?.currentPosition ?: 0
        binding.videoView.player = null
        exoPlayer?.release()
        this.exoPlayer = null
    }

    companion object {
        const val ATTACHMENT = "ATTACHMENT"
        private const val VIDEO_PLAY_WHEN_READY = "VIDEO_STATE"
        private const val VIDEO_POSITION = "VIDEO_POS"
    }
}
