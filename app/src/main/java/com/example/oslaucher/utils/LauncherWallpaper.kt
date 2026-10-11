package com.example.oslaucher.utils

import android.app.WallpaperManager
import android.graphics.drawable.Drawable
import android.view.View

object LauncherWallpaper {
    fun applyTo(view: View) {
        val wallpaper = WallpaperManager.getInstance(view.context).drawable ?: return
        view.background = copyDrawable(wallpaper, view)
    }

    private fun copyDrawable(drawable: Drawable, view: View): Drawable =
        drawable.constantState
            ?.newDrawable(view.resources)
            ?.mutate()
            ?: drawable.mutate()
}
