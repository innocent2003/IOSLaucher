package com.example.oslaucher.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

internal class CenterCropDrawable(
    private val source: Drawable
) : Drawable() {
    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val sourceWidth = source.intrinsicWidth
        val sourceHeight = source.intrinsicHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            source.bounds = bounds
            source.draw(canvas)
            return
        }

        val scale = maxOf(
            bounds.width().toFloat() / sourceWidth,
            bounds.height().toFloat() / sourceHeight
        )
        val saveCount = canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(
            bounds.centerX() - sourceWidth * scale / 2f,
            bounds.centerY() - sourceHeight * scale / 2f
        )
        canvas.scale(scale, scale)
        source.setBounds(0, 0, sourceWidth, sourceHeight)
        source.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight
}
