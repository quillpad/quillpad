package org.qosp.notes.ui.utils.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import coil.bitmap.BitmapPool
import coil.decode.DataSource
import coil.decode.DecodeUtils
import coil.decode.Options
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.OriginalSize
import coil.size.PixelSize
import coil.size.Size
import org.qosp.notes.R
import org.qosp.notes.ui.attachments.getAlbumArtBitmap
import org.qosp.notes.ui.utils.getDrawableCompat
import kotlin.math.roundToInt

// Modified VideoFrameFetcher to load album art images
// TODO: Resizing doesn't work, need to fix it
class AlbumArtFetcher(private val context: Context) : Fetcher<Uri> {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override suspend fun fetch(pool: BitmapPool, data: Uri, size: Size, options: Options): FetchResult {

        val defaultDrawable = context.getDrawableCompat(R.drawable.ic_music) ?: ColorDrawable(Color.BLACK)
        DrawableCompat.setTint(
            defaultDrawable,
            Color.GRAY
        )

        val defaultResult = DrawableResult(
            defaultDrawable,
            false,
            DataSource.DISK,
        )

        val rawBitmap = getAlbumArtBitmap(context, data) ?: return defaultResult

        val srcWidth = rawBitmap.width
        val srcHeight = rawBitmap.height

        val destSize = when (size) {
            is PixelSize -> {
                if (srcWidth > 0 && srcHeight > 0) {
                    val rawScale = DecodeUtils.computeSizeMultiplier(
                        srcWidth = srcWidth,
                        srcHeight = srcHeight,
                        dstWidth = size.width,
                        dstHeight = size.height,
                        scale = options.scale
                    )
                    val scale = if (options.allowInexactSize) rawScale.coerceAtMost(1.0) else rawScale
                    val width = (scale * srcWidth).roundToInt()
                    val height = (scale * srcHeight).roundToInt()
                    PixelSize(width, height)
                } else {
                    OriginalSize
                }
            }
            is OriginalSize -> OriginalSize
        }

        val bitmap = normalizeBitmap(pool, rawBitmap, destSize, options)

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

    override fun key(data: Uri) = data.toString()

    private fun normalizeBitmap(
        pool: BitmapPool,
        inBitmap: Bitmap,
        size: Size,
        options: Options
    ): Bitmap {
        // Fast path: if the input bitmap is valid, return it.
        if (isConfigValid(inBitmap, options) && isSizeValid(inBitmap, options, size)) {
            return inBitmap
        }

        // Slow path: re-render the bitmap with the correct size + config.
        val scale: Float
        val dstWidth: Int
        val dstHeight: Int
        when (size) {
            is PixelSize -> {
                scale = DecodeUtils.computeSizeMultiplier(
                    srcWidth = inBitmap.width,
                    srcHeight = inBitmap.height,
                    dstWidth = size.width,
                    dstHeight = size.height,
                    scale = options.scale
                ).toFloat()
                dstWidth = (scale * inBitmap.width).roundToInt()
                dstHeight = (scale * inBitmap.height).roundToInt()
            }
            is OriginalSize -> {
                scale = 1f
                dstWidth = inBitmap.width
                dstHeight = inBitmap.height
            }
        }
        val safeConfig = when {
            Build.VERSION.SDK_INT >= 26 && options.config == Bitmap.Config.HARDWARE -> Bitmap.Config.ARGB_8888
            else -> options.config
        }

        val outBitmap = pool.get(dstWidth, dstHeight, safeConfig)
        outBitmap.applyCanvas {
            scale(scale, scale)
            drawBitmap(inBitmap, 0f, 0f, paint)
        }
        pool.put(inBitmap)

        return outBitmap
    }

    private fun isConfigValid(bitmap: Bitmap, options: Options): Boolean {
        return Build.VERSION.SDK_INT < 26 || bitmap.config != Bitmap.Config.HARDWARE || options.config == Bitmap.Config.HARDWARE
    }

    private fun isSizeValid(bitmap: Bitmap, options: Options, size: Size): Boolean {
        return options.allowInexactSize || size is OriginalSize ||
            size == DecodeUtils.computePixelSize(bitmap.width, bitmap.height, size, options.scale)
    }
}
