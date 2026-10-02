package org.qosp.notes.ui.utils.coil

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.request.Options
import coil.size.Size
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [AlbumArtFetcher].
 *
 * Test media assets (see app/src/test/resources/test_media/):
 * - test_audio_with_art.mp3  — CC-licensed audio with embedded JPEG album art
 *   (audio: CC BY-SA 3.0 Example.ogg from Wikimedia; image: GFDL Camponotus ant photo)
 * - test_audio_no_art.ogg    — CC BY-SA 3.0 audio WITHOUT embedded album art
 * - test_image.jpg           — GFDL JPEG used as embedded art source
 *
 * Full attribution: app/src/test/resources/test_media/CREDITS.md
 */
@RunWith(RobolectricTestRunner::class)
class AlbumArtFetcherTest {

    private lateinit var mockContext: Context
    private lateinit var mockImageLoader: ImageLoader
    private lateinit var options: Options

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockImageLoader = mockk(relaxed = true)
        options = Options(context = mockContext, size = Size(480, 480))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Factory — MIME-type routing
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `Factory creates fetcher for audio-slash-mpeg MIME type`() {
        val uri = Uri.parse("content://media/audio/1")
        every { mockContext.contentResolver.getType(uri) } returns "audio/mpeg"

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNotNull("Expected a fetcher for audio/mpeg MIME type", fetcher)
        assertTrue(fetcher is AlbumArtFetcher)
    }

    @Test
    fun `Factory creates fetcher for audio-slash-mp4 MIME type`() {
        val uri = Uri.parse("content://media/audio/2")
        every { mockContext.contentResolver.getType(uri) } returns "audio/mp4"

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNotNull(fetcher)
    }

    @Test
    fun `Factory creates fetcher for ogg file extension when MIME is unavailable`() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/song.ogg")
        every { mockContext.contentResolver.getType(uri) } returns null

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNotNull("Expected fetcher for .ogg extension", fetcher)
    }

    @Test
    fun `Factory creates fetcher for mp3 file extension when MIME is unavailable`() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/track.mp3")
        every { mockContext.contentResolver.getType(uri) } returns null

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNotNull(fetcher)
    }

    @Test
    fun `Factory creates fetcher for flac file extension`() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/track.flac")
        every { mockContext.contentResolver.getType(uri) } returns null

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNotNull(fetcher)
    }

    @Test
    fun `Factory returns null for image-slash-png MIME type`() {
        val uri = Uri.parse("content://media/images/1")
        every { mockContext.contentResolver.getType(uri) } returns "image/png"

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNull("Expected null fetcher for image/png — not an audio file", fetcher)
    }

    @Test
    fun `Factory returns null for video-slash-mp4 MIME type`() {
        val uri = Uri.parse("content://media/video/1")
        every { mockContext.contentResolver.getType(uri) } returns "video/mp4"

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNull(fetcher)
    }

    @Test
    fun `Factory returns null for text-slash-plain MIME type`() {
        val uri = Uri.parse("content://com.example/doc/1")
        every { mockContext.contentResolver.getType(uri) } returns "text/plain"

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNull(fetcher)
    }

    @Test
    fun `Factory returns null for jpg file extension`() {
        val uri = Uri.parse("file:///storage/emulated/0/photo.jpg")
        every { mockContext.contentResolver.getType(uri) } returns null

        val fetcher = AlbumArtFetcher.Factory(mockContext).create(uri, options, mockImageLoader)

        assertNull(fetcher)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // extractEmbeddedPicture — pure byte extraction logic (mocked retriever)
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `extractEmbeddedPicture returns bytes when retriever provides picture`() {
        val fakeJpegBytes = ByteArray(512) { it.toByte() }
        val uri = Uri.parse("content://media/audio/99")

        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(mockContext, uri) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns fakeJpegBytes

        val result = AlbumArtFetcher.extractEmbeddedPicture(mockContext, uri)

        assertNotNull("Expected non-null bytes when retriever returns picture", result)
        assertTrue("Byte array should have expected length", result!!.size == 512)
    }

    @Test
    fun `extractEmbeddedPicture returns null when retriever has no embedded picture`() {
        val uri = Uri.parse("content://media/audio/100")

        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(mockContext, uri) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns null

        val result = AlbumArtFetcher.extractEmbeddedPicture(mockContext, uri)

        assertNull("Expected null when retriever has no embedded picture", result)
    }

    @Test
    fun `extractEmbeddedPicture returns null when retriever throws`() {
        val uri = Uri.parse("content://invalid/uri")

        mockkConstructor(MediaMetadataRetriever::class)
        every {
            anyConstructed<MediaMetadataRetriever>().setDataSource(mockContext, uri)
        } throws IllegalArgumentException("Cannot open datasource")

        val result = AlbumArtFetcher.extractEmbeddedPicture(mockContext, uri)

        assertNull("Expected null when retriever throws an exception", result)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // isAudioUri helper
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `isAudioUri returns true for all known audio extensions`() {
        val extensions = listOf("mp3", "m4a", "ogg", "oga", "flac", "wav", "opus", "aac", "wma", "aiff")
        extensions.forEach { ext ->
            val uri = Uri.parse("file:///storage/music/track.$ext")
            every { mockContext.contentResolver.getType(uri) } returns null
            assertTrue("Expected isAudioUri=true for .$ext", AlbumArtFetcher.isAudioUri(mockContext, uri))
        }
    }

    @Test
    fun `isAudioUri returns false for image and video extensions`() {
        val nonAudio = listOf("jpg", "png", "mp4", "mkv", "pdf", "txt")
        nonAudio.forEach { ext ->
            val uri = Uri.parse("file:///storage/files/file.$ext")
            every { mockContext.contentResolver.getType(uri) } returns null
            assertTrue("Expected isAudioUri=false for .$ext", !AlbumArtFetcher.isAudioUri(mockContext, uri))
        }
    }
}
