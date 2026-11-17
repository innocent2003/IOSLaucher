package com.example.oslaucher.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.roundToInt

/**
 * Gives every app icon the same rounded-square shape and corner radius.
 *
 * Apps ship icons in many shapes (adaptive icons masked by the OEM, pre-rounded squares with
 * their own radius, circles, icons with transparent padding). Each one is redrawn into one
 * bitmap clipped to the same rounded square:
 * - adaptive icons: background + foreground layers fill the square (no OEM mask);
 * - full-bleed legacy icons: cropped to their content and stretched to the square, with
 *   transparent corners filled by the icon's own edge colour;
 * - small/padded legacy icons: centred on a white rounded square.
 */
object IconShapeUtils {
    /** Corner radius as a fraction of the icon size (≈ the reference launcher's look). */
    private const val CORNER_RATIO = 0.225f
    private const val MAX_ICON_PX = 192
    private const val PADDED_ICON_SCALE = 0.72f
    private const val FULL_BLEED_COVERAGE = 0.9f

    fun normalize(context: Context, source: Drawable): Drawable {
        val size = IconMetrics.iconSizePx(context).coerceIn(48, MAX_ICON_PX)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val radius = size * CORNER_RATIO
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source is AdaptiveIconDrawable) {
            // Layers are 108dp with the visible 72dp in the middle: extend 25% on each side.
            val extra = (size * 0.25f).roundToInt()
            val bounds = Rect(-extra, -extra, size + extra, size + extra)
            source.background?.let {
                it.bounds = bounds
                it.draw(canvas)
            } ?: canvas.drawColor(Color.WHITE)
            source.foreground?.let {
                it.bounds = bounds
                it.draw(canvas)
            }
            return BitmapDrawable(context.resources, output)
        }

        val raw = toBitmap(source, size * 2)
        val content = opaqueBounds(raw)
        if (content == null) {
            canvas.drawColor(Color.WHITE)
            return BitmapDrawable(context.resources, output)
        }
        val coverage = minOf(
            content.width().toFloat() / raw.width,
            content.height().toFloat() / raw.height
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (coverage >= FULL_BLEED_COVERAGE) {
            // Fill the corners the icon leaves transparent (rounder/circular icons) with its
            // own edge colour, then stretch its content over the whole square.
            canvas.drawColor(edgeColor(raw, content))
            canvas.drawBitmap(raw, content, Rect(0, 0, size, size), paint)
        } else {
            canvas.drawColor(Color.WHITE)
            val scale = size * PADDED_ICON_SCALE / maxOf(content.width(), content.height())
            val width = content.width() * scale
            val height = content.height() * scale
            val left = (size - width) / 2f
            val top = (size - height) / 2f
            canvas.drawBitmap(raw, content, RectF(left, top, left + width, top + height), paint)
        }
        raw.recycle()
        return BitmapDrawable(context.resources, output)
    }

    private fun toBitmap(drawable: Drawable, size: Int): Bitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = size.toFloat() / maxOf(width, height)
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(canvas)
        drawable.bounds = oldBounds
        return bitmap
    }

    /** Bounding box of the clearly visible pixels, or null for an empty icon. */
    private fun opaqueBounds(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (Color.alpha(pixels[row + x]) > 40) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < left || bottom < top) null else Rect(left, top, right + 1, bottom + 1)
    }

    /** Colour just inside the middle of the content's left edge (the icon's background). */
    private fun edgeColor(bitmap: Bitmap, content: Rect): Int {
        val inset = (content.width() * 0.04f).roundToInt().coerceAtLeast(1)
        val x = (content.left + inset).coerceIn(0, bitmap.width - 1)
        val y = content.centerY().coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(x, y)
        return if (Color.alpha(pixel) > 200) {
            Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        } else {
            Color.WHITE
        }
    }
}
