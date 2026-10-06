package com.example.oslaucher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height) - dp(16f)
        if (diameter <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = diameter / 2f

        paint.color = 0xFFF8F8F5.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)

        paint.color = 0xFF202124.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        canvas.drawCircle(centerX, centerY, radius - dp(1f), paint)

        for (mark in 0 until 60) {
            val angle = Math.toRadians(mark * 6.0 - 90.0)
            val major = mark % 5 == 0
            val outer = radius - dp(if (major) 7f else 5f)
            val inner = radius - dp(if (major) 14f else 9f)
            paint.color = if (major) 0xFF191A1B.toInt() else 0xFF777777.toInt()
            paint.strokeWidth = dp(if (major) 1.6f else 0.7f)
            canvas.drawLine(
                centerX + cos(angle).toFloat() * inner,
                centerY + sin(angle).toFloat() * inner,
                centerX + cos(angle).toFloat() * outer,
                centerY + sin(angle).toFloat() * outer,
                paint
            )
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF171819.toInt()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = radius * 0.18f
        for (number in 1..12) {
            val angle = Math.toRadians(number * 30.0 - 90.0)
            val text = number.toString()
            paint.getTextBounds(text, 0, text.length, textBounds)
            val x = centerX + cos(angle).toFloat() * radius * 0.70f -
                textBounds.exactCenterX()
            val y = centerY + sin(angle).toFloat() * radius * 0.70f -
                textBounds.exactCenterY()
            canvas.drawText(text, x, y, paint)
        }

        val time = Calendar.getInstance()
        val minute = time.get(Calendar.MINUTE)
        val hour = time.get(Calendar.HOUR)
        val second = time.get(Calendar.SECOND)
        drawHand(canvas, centerX, centerY, radius * 0.48f, (hour + minute / 60f) * 30f, dp(3f))
        drawHand(canvas, centerX, centerY, radius * 0.68f, minute * 6f, dp(2f))
        drawHand(canvas, centerX, centerY, radius * 0.74f, second * 6f, dp(0.8f), 0xFFE49A35.toInt())

        paint.color = 0xFF222222.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dp(3f), paint)
    }

    private fun drawHand(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        length: Float,
        degrees: Float,
        strokeWidth: Float,
        color: Int = 0xFF202124.toInt()
    ) {
        val angle = Math.toRadians(degrees - 90.0)
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            centerX,
            centerY,
            centerX + cos(angle).toFloat() * length,
            centerY + sin(angle).toFloat() * length,
            paint
        )
    }

    private fun dp(value: Float): Float = value * density
}
