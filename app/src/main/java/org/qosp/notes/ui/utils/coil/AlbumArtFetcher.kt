package org.qosp.notes.ui.utils.coil

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * Custom Coil [Fetcher] that extracts embedded album artwork from audio attachments
 * as an [ImageSource] stream without performing manual unconstrained bitmap allocations.
 * Coil's decoder pipeline will downsample and cache the result according to requested [Options].
 */
class AlbumArtFetcher(
    private val context: Context,
    private val data: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val picture = extractEmbeddedPicture(context, data) ?: return null
        val buffer = Buffer().write(picture)
        return SourceResult(
            source = ImageSource(source = buffer, context = context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (isAudioUri(context, data)) {
                return AlbumArtFetcher(context, data, options)
            }
            return null
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "ogg", "oga", "flac", "wav", "opus", "aac", "mid", "midi", "wma", "aiff"
        )

        fun isAudioUri(context: Context, uri: Uri): Boolean {
            val mimeType = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) {
                null
            }
            if (mimeType?.startsWith("audio", ignoreCase = true) == true) {
                return true
            }
            val path = uri.path ?: uri.toString()
            val extension = path.substringAfterLast('.', "").lowercase()
            return extension in AUDIO_EXTENSIONS
        }

        fun extractEmbeddedPicture(context: Context, uri: Uri): ByteArray? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
