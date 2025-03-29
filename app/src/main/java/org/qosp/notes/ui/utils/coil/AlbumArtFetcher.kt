package org.qosp.notes.ui.utils.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import coil.decode.DataSource
import coil.decode.DecodeUtils
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import coil.size.Scale
import coil.size.Size
import coil.size.isOriginal
import coil.size.pxOrElse
import org.qosp.notes.R
import org.qosp.notes.ui.attachments.getAlbumArtBitmap
import org.qosp.notes.ui.utils.getDrawableCompat
import kotlin.math.roundToInt

// Modified VideoFrameFetcher to load album art images
// TODO: Resizing doesn't work, need to fix it
class AlbumArtFetcher(
    private val context: Context,
    private val data: Uri,
    private val options: Options
) : Fetcher {

    private fun logv(string: String) = Log.i("AlbulmFetcher", string)

    override suspend fun fetch(): FetchResult {

        val defaultDrawable =
            context.getDrawableCompat(R.drawable.ic_music) ?: ColorDrawable(Color.BLACK)
        DrawableCompat.setTint(defaultDrawable, Color.GRAY)

        val defaultResult = DrawableResult(defaultDrawable, false, DataSource.DISK)
        logv("Getting URL $data")
        val rawBitmap = getAlbumArtBitmap(context, data) ?: return defaultResult

        logv("Got Audio bitmap : ${rawBitmap.height}x${rawBitmap.width}")
        val srcWidth = rawBitmap.width
        val srcHeight = rawBitmap.height

        val dstSize = if (srcWidth > 0 && srcHeight > 0) {
            val dstWidth = options.size.widthPx(options.scale) { srcWidth }
            val dstHeight = options.size.heightPx(options.scale) { srcHeight }
            val rawScale = DecodeUtils.computeSizeMultiplier(
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                dstWidth = dstWidth,
                dstHeight = dstHeight,
                scale = options.scale
            )
            val scale = if (options.allowInexactSize) {
                rawScale.coerceAtMost(1.0)
            } else {
                rawScale
            }
            val width = (scale * srcWidth).roundToInt()
            val height = (scale * srcHeight).roundToInt()
            Size(width, height)
        } else {
            // We were unable to decode the video's dimensions.
            // Fall back to decoding the video frame at the original size.
            // We'll scale the resulting bitmap after decoding if necessary.
            Size.ORIGINAL
        }

        val bitmap = normalizeBitmap(rawBitmap, dstSize)

        val isSampled = if (srcWidth > 0 && srcHeight > 0) {
            DecodeUtils.computeSizeMultiplier(
                srcWidth,
                srcHeight,
                bitmap.width,
                bitmap.height,
                options.scale
            ) < 1.0
        } else {
            true
        }

        return DrawableResult(bitmap.toDrawable(context.resources), isSampled, DataSource.DISK)
    }

    private fun normalizeBitmap(inBitmap: Bitmap, size: Size): Bitmap {
        // Fast path: if the input bitmap is valid, return it.
        if (isConfigValid(inBitmap, options) && isSizeValid(inBitmap, options, size)) {
            return inBitmap
        }

        // Slow path: re-render the bitmap with the correct size + config.
        val scale = DecodeUtils.computeSizeMultiplier(
            srcWidth = inBitmap.width,
            srcHeight = inBitmap.height,
            dstWidth = size.width.pxOrElse { inBitmap.width },
            dstHeight = size.height.pxOrElse { inBitmap.height },
            scale = options.scale
        ).toFloat()
        val dstWidth = (scale * inBitmap.width).roundToInt()
        val dstHeight = (scale * inBitmap.height).roundToInt()
        val safeConfig = when {
            Build.VERSION.SDK_INT >= 26 && options.config == Bitmap.Config.HARDWARE -> Bitmap.Config.ARGB_8888
            else -> options.config
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val outBitmap = createBitmap(dstWidth, dstHeight, safeConfig)
        outBitmap.applyCanvas {
            scale(scale, scale)
            drawBitmap(inBitmap, 0f, 0f, paint)
        }
        inBitmap.recycle()

        return outBitmap
    }

    private fun isConfigValid(bitmap: Bitmap, options: Options): Boolean {
        return Build.VERSION.SDK_INT < 26 || bitmap.config != Bitmap.Config.HARDWARE || options.config == Bitmap.Config.HARDWARE
    }

    private fun isSizeValid(bitmap: Bitmap, options: Options, size: Size): Boolean {
        if (options.allowInexactSize) return true
        val multiplier = DecodeUtils.computeSizeMultiplier(
            srcWidth = bitmap.width,
            srcHeight = bitmap.height,
            dstWidth = size.width.pxOrElse { bitmap.width },
            dstHeight = size.height.pxOrElse { bitmap.height },
            scale = options.scale
        )
        return multiplier == 1.0
    }
}

internal inline fun Size.widthPx(scale: Scale, original: () -> Int): Int {
    return if (isOriginal) original() else width.toPx(scale)
}

internal inline fun Size.heightPx(scale: Scale, original: () -> Int): Int {
    return if (isOriginal) original() else height.toPx(scale)
}

internal fun Dimension.toPx(scale: Scale) = pxOrElse {
    when (scale) {
        Scale.FILL -> Int.MIN_VALUE
        Scale.FIT -> Int.MAX_VALUE
    }
}
