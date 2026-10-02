package org.qosp.notes.ui.attachments

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.data.model.Attachment
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [AttachmentUtils] — MIME-type classification and URI resolution helpers.
 *
 * Test assets in app/src/test/resources/test_media/:
 *  - test_image.jpg           — GFDL JPEG (Camponotus ant photo, Muhammad Mahdi Karim, Wikimedia)
 *  - test_audio_with_art.mp3  — CC BY-SA 3.0 audio with embedded JPEG cover
 *  - test_audio_no_art.ogg    — CC BY-SA 3.0 audio without cover art
 *
 * Full attribution: app/src/test/resources/test_media/CREDITS.md
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Attachment.fromUri — MIME-type → Attachment.Type mapping
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `fromUri assigns IMAGE type for image slash jpeg MIME`() {
        val uri = Uri.parse("content://media/images/1")
        every { mockContentResolver.getType(uri) } returns "image/jpeg"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.IMAGE, attachment.type)
    }

    @Test
    fun `fromUri assigns IMAGE type for image slash png MIME`() {
        val uri = Uri.parse("content://media/images/2")
        every { mockContentResolver.getType(uri) } returns "image/png"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.IMAGE, attachment.type)
    }

    @Test
    fun `fromUri assigns VIDEO type for video slash mp4 MIME`() {
        val uri = Uri.parse("content://media/video/1")
        every { mockContentResolver.getType(uri) } returns "video/mp4"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.VIDEO, attachment.type)
    }

    @Test
    fun `fromUri assigns AUDIO type for audio slash mpeg MIME`() {
        val uri = Uri.parse("content://media/audio/1")
        every { mockContentResolver.getType(uri) } returns "audio/mpeg"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.AUDIO, attachment.type)
    }

    @Test
    fun `fromUri assigns AUDIO type for audio slash ogg MIME`() {
        val uri = Uri.parse("content://media/audio/2")
        every { mockContentResolver.getType(uri) } returns "audio/ogg"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.AUDIO, attachment.type)
    }

    @Test
    fun `fromUri assigns GENERIC type for application slash pdf MIME`() {
        val uri = Uri.parse("content://docs/1")
        every { mockContentResolver.getType(uri) } returns "application/pdf"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.GENERIC, attachment.type)
    }

    @Test
    fun `fromUri assigns GENERIC type when MIME is null`() {
        val uri = Uri.parse("content://unknown/1")
        every { mockContentResolver.getType(uri) } returns null
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals(Attachment.Type.GENERIC, attachment.type)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // getAttachmentUri — URI path resolution
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `getAttachmentUri returns parsed URI for content scheme`() {
        val contentUri = "content://com.example.provider/files/song.mp3"
        val uri = getAttachmentUri(mockContext, contentUri)
        assertEquals(Uri.parse(contentUri), uri)
    }

    @Test
    fun `getAttachmentUri returns parsed URI for file scheme`() {
        val fileUri = "file:///sdcard/music/track.ogg"
        val uri = getAttachmentUri(mockContext, fileUri)
        assertEquals(Uri.parse(fileUri), uri)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // getAttachmentFilename — graceful null-safety under query failure
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `getAttachmentFilename returns null when contentResolver query throws`() {
        val uri = Uri.parse("content://invalid/uri")
        every { mockContentResolver.query(uri, null, null, null, null) } throws RuntimeException("DB error")

        val filename = getAttachmentFilename(mockContext, uri)

        assertNull("Expected null when query throws", filename)
    }

    @Test
    fun `getAttachmentFilename returns null when contentResolver returns null cursor`() {
        val uri = Uri.parse("content://media/audio/999")
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val filename = getAttachmentFilename(mockContext, uri)

        assertNull(filename)
    }

    @Test
    fun `fromUri uses empty string description when filename cannot be resolved`() {
        val uri = Uri.parse("content://media/audio/3")
        every { mockContentResolver.getType(uri) } returns "audio/mp4"
        every { mockContentResolver.query(uri, null, null, null, null) } returns null

        val attachment = Attachment.fromUri(mockContext, uri)

        assertEquals("", attachment.description)
    }
}
