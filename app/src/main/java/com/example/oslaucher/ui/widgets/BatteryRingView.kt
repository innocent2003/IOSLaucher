package com.example.oslaucher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class BatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    var batteryLevel: Int? = null
        set(value) {
            field = value?.coerceIn(0, 100)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = dp(5f)
        val radius = (min(width, height) - stroke) / 2f
        if (radius <= 0f) return
        val centerX = width / 2f
        val centerY = height / 2f
        val bounds = centerX - radius

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFF42484A.toInt()
        canvas.drawCircle(centerX, centerY, radius, paint)

        batteryLevel?.let { level ->
            paint.color = if (level <= 20) 0xFFE65252.toInt() else 0xFF66CB70.toInt()
            canvas.drawArc(
                RectF(bounds, bounds, bounds + radius * 2f, bounds + radius * 2f),
                -90f,
                360f * level / 100f,
                false,
                paint
            )
        }

        val iconWidth = dp(13f)
        val iconHeight = dp(20f)
        val left = centerX - iconWidth / 2f
        val top = centerY - iconHeight / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.8f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFFE5E8E8.toInt()
        canvas.drawRoundRect(left, top, left + iconWidth, top + iconHeight, dp(2f), dp(2f), paint)
        canvas.drawLine(
            centerX,
            top - dp(2.5f),
            centerX,
            top - dp(0.5f),
            paint
        )
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, top + iconHeight - dp(2.8f), dp(0.7f), paint)
    }

    private fun dp(value: Float): Float = value * density
}
