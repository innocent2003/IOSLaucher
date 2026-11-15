package com.example.oslaucher.utils

import android.content.Context
import kotlin.math.roundToInt

/**
 * Icon/label sizes derived from the screen width, matching the reference home screen
 * (icon ≈ 15% of the screen width, label ≈ 21% of the icon). Sizing from the screen width
 * instead of fixed dp/sp keeps the same proportions on every display-size and font-size
 * setting, so labels stay small and icons stay big.
 */
object IconMetrics {
    private const val ICON_WIDTH_RATIO = 0.152f
    private const val LABEL_ICON_RATIO = 0.21f
    private const val DOCK_HEIGHT_ICON_RATIO = 1.46f

    fun iconSizePx(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        return (shortSide * ICON_WIDTH_RATIO).roundToInt().coerceAtLeast(1)
    }

    fun labelTextSizePx(context: Context): Float = iconSizePx(context) * LABEL_ICON_RATIO

    fun dockHeightPx(context: Context): Int =
        (iconSizePx(context) * DOCK_HEIGHT_ICON_RATIO).roundToInt()
}
