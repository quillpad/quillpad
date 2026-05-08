package org.qosp.notes.components

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.qosp.notes.BuildConfig
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.attachments.getAttachmentUri
import java.io.File

class MediaStorageManager(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val mediaFolder: String,
) {
    private val directory get() = File(context.filesDir, mediaFolder)
        .also { it.mkdir() }

    fun listMediaFiles(): List<String> {
        return directory
            .list()
            .orEmpty()
            .toList()
    }

    fun deleteAllMedia() {
        directory.deleteRecursively()
    }

    suspend fun cleanUpStorage() = runCatching {
        val notes = noteRepository
            .getAll()
            .first()
        val filesUsed = notes
            .flatMap { note ->
                note.attachments.map { it.path } + note.content.markdownImageDestinations()
            }
            .toSet()

        val files = directory
            .list()
            .orEmpty()

        for (file in files) {
            if (getAttachmentUri(context, file, mediaFolder).toString() !in filesUsed) {
                File(directory, file).delete()
            }
        }
    }

    suspend fun copyMediaToPrivateStorage(uri: Uri, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        val mediaType = when {
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            else -> return@withContext null
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" }
            ?: mediaType.defaultExtension
        val (newUri, file) = createMediaFile(mediaType, extension) ?: return@withContext null

        val copied = try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                file.delete()
                return@withContext null
            }
            input.use { source ->
                file.outputStream().use { target ->
                    source.copyTo(target)
                }
            }
            true
        } catch (error: CancellationException) {
            file.delete()
            throw error
        } catch (error: Exception) {
            false
        }

        if (!copied) {
            file.delete()
            return@withContext null
        }

        newUri
    }

    /***
     * Creates a media file in local storage.
     *
     * @return The file's [Uri] and [File] objects.
     */
    suspend fun createMediaFile(type: MediaType, extension: String = type.defaultExtension): Pair<Uri, File>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val prefix = when (type) {
                    MediaType.IMAGE -> "img_"
                    MediaType.AUDIO -> "audio_"
                    MediaType.VIDEO -> "video_"
                }

                val file = File.createTempFile(prefix, extension, directory)
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file) to file
            }.getOrNull()
        }
    }

    enum class MediaType(val defaultExtension: String) {
        IMAGE(".jpg"),
        VIDEO(".mp4"),
        AUDIO(".mp3")
    }

    private fun String.markdownImageDestinations(): List<String> {
        if (!contains("![")) return emptyList()

        val destinations = mutableListOf<String>()
        Parser.builder()
            .build()
            .parse(this)
            .accept(object : AbstractVisitor() {
                override fun visit(image: Image) {
                    image.destination?.let(destinations::add)
                    visitChildren(image)
                }
            })

        return destinations
    }
}
