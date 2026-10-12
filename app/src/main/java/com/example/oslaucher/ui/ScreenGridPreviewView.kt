package com.example.oslaucher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class ScreenGridPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var gridRows: Int = ScreenGridActivity.DEFAULT_GRID_ROWS
        set(value) {
            field = value.coerceIn(
                ScreenGridActivity.MIN_GRID_ROWS,
                ScreenGridActivity.MAX_GRID_ROWS
            )
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val phone = RectF()
    private val icon = RectF()
    private val dock = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val previewHeight = height * 0.84f
        val phoneWidth = minOf(width * 0.66f, previewHeight / 2.18f)
        val phoneHeight = phoneWidth * 2.18f
        val left = (width - phoneWidth) / 2f
        val top = (height - phoneHeight) / 2f
        phone.set(left, top, left + phoneWidth, top + phoneHeight)

        val saveCount = canvas.save()
        val phoneClip = Path().apply {
            addRoundRect(
                phone,
                phoneWidth * 0.105f,
                phoneWidth * 0.105f,
                Path.Direction.CW
            )
        }
        canvas.clipPath(phoneClip)
        paint.shader = LinearGradient(
            phone.left,
            phone.top,
            phone.right,
            phone.bottom,
            intArrayOf(0xFFD4B89D.toInt(), 0xFF776458.toInt(), 0xFF25283D.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(phone, paint)
        paint.shader = null

        drawWallpaperCurves(canvas, phoneWidth)
        drawAppGrid(canvas, phoneWidth)
        drawDock(canvas)
        canvas.restoreToCount(saveCount)
        drawScrollBar(canvas, density)
    }

    private fun drawWallpaperCurves(canvas: Canvas, phoneWidth: Float) {
        val wave = Path().apply {
            moveTo(phone.left, phone.top + phone.height() * 0.03f)
            cubicTo(
                phone.left + phone.width() * 0.25f,
                phone.top - phone.height() * 0.08f,
                phone.right + phone.width() * 0.05f,
                phone.top + phone.height() * 0.03f,
                phone.right,
                phone.top + phone.height() * 0.12f
            )
            lineTo(phone.right, phone.top + phone.height() * 0.32f)
            cubicTo(
                phone.right - phone.width() * 0.12f,
                phone.top + phone.height() * 0.47f,
                phone.left + phone.width() * 0.38f,
                phone.top + phone.height() * 0.33f,
                phone.left,
                phone.top + phone.height() * 0.50f
            )
            close()
        }
        paint.shader = LinearGradient(
            phone.left,
            phone.top,
            phone.right,
            phone.top + phone.height() * 0.52f,
            0x99F2DEC5.toInt(),
            0x337D685B,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(wave, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = phoneWidth * 0.008f
        paint.color = 0xCCF0E1D0.toInt()
        val curve = Path().apply {
            moveTo(phone.left - phoneWidth * 0.04f, phone.top + phone.height() * 0.57f)
            cubicTo(
                phone.left + phone.width() * 0.24f,
                phone.top + phone.height() * 0.35f,
                phone.right - phone.width() * 0.18f,
                phone.top + phone.height() * 0.70f,
                phone.right + phoneWidth * 0.04f,
                phone.top + phone.height() * 0.48f
            )
            cubicTo(
                phone.right - phone.width() * 0.02f,
                phone.top + phone.height() * 0.76f,
                phone.left + phone.width() * 0.04f,
                phone.top + phone.height() * 0.73f,
                phone.left - phoneWidth * 0.02f,
                phone.top + phone.height() * 0.91f
            )
        }
        canvas.drawPath(curve, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAppGrid(canvas: Canvas, phoneWidth: Float) {
        val cellSize = phoneWidth * 0.15f
        val horizontalGap = (phone.width() - 2f * phoneWidth * 0.075f - 4f * cellSize) / 3f
        val gridTop = phone.top + phone.height() * 0.105f
        val gridBottom = phone.top + phone.height() * 0.72f
        val verticalGap = (gridBottom - gridTop - gridRows * cellSize) / (gridRows - 1)

        repeat(gridRows) { row ->
            repeat(4) { column ->
                val x = phone.left + phoneWidth * 0.075f + column * (cellSize + horizontalGap)
                val y = gridTop + row * (cellSize + verticalGap)
                icon.set(x, y, x + cellSize, y + cellSize)
                paint.color = 0xBBDDDDDC.toInt()
                canvas.drawRoundRect(icon, cellSize * 0.25f, cellSize * 0.25f, paint)
                paint.color = 0x22FFFFFF
                canvas.drawRoundRect(icon, cellSize * 0.25f, cellSize * 0.25f, paint)
            }
        }
    }

    private fun drawDock(canvas: Canvas) {
        val dockWidth = phone.width() * 0.88f
        val dockHeight = phone.height() * 0.105f
        val left = phone.centerX() - dockWidth / 2f
        val top = phone.bottom - phone.height() * 0.125f - dockHeight / 2f
        dock.set(left, top, left + dockWidth, top + dockHeight)
        paint.color = 0x557F8999
        canvas.drawRoundRect(dock, dockHeight * 0.45f, dockHeight * 0.45f, paint)

        val iconSize = dockHeight * 0.62f
        val gap = (dock.width() - 4f * iconSize) / 5f
        repeat(4) { index ->
            val x = dock.left + gap + index * (iconSize + gap)
            val y = dock.centerY() - iconSize / 2f
            icon.set(x, y, x + iconSize, y + iconSize)
            paint.color = 0xBBDDDDDC.toInt()
            canvas.drawRoundRect(icon, iconSize * 0.24f, iconSize * 0.24f, paint)
        }
    }

    private fun drawScrollBar(canvas: Canvas, density: Float) {
        val trackWidth = 4f * density
        val trackLeft = phone.right + 16f * density
        val trackTop = phone.top
        val trackBottom = phone.bottom
        paint.color = 0xFF242426.toInt()
        canvas.drawRoundRect(
            trackLeft,
            trackTop,
            trackLeft + trackWidth,
            trackBottom,
            trackWidth / 2f,
            trackWidth / 2f,
            paint
        )
        val thumbHeight = phone.height() * (gridRows + 1f) / 11f
        val thumbTop = trackTop + phone.height() * 0.09f
        paint.color = 0xFF078BFF.toInt()
        canvas.drawRoundRect(
            trackLeft,
            thumbTop,
            trackLeft + trackWidth,
            minOf(thumbTop + thumbHeight, trackBottom),
            trackWidth / 2f,
            trackWidth / 2f,
            paint
        )
    }
}
