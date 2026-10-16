package com.example.oslaucher.utils

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import java.io.IOException

object LauncherWallpaper {
    fun applyTo(view: View): Boolean {
        return try {
            val preferences = view.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val selectedUri = preferences.getString(URI_KEY, null)?.let(Uri::parse)
            val selectedWallpaper = try {
                when {
                    selectedUri != null -> {
                        view.context.contentResolver.openInputStream(selectedUri)?.use {
                            Drawable.createFromStream(it, null)
                        }.also { drawable ->
                            if (drawable == null) {
                                Log.w(TAG, "Could not read the saved wallpaper URI: $selectedUri")
                            }
                        }
                    }
                    preferences.contains(RESOURCE_KEY) ->
                        view.context.getDrawable(preferences.getInt(RESOURCE_KEY, 0))
                    else -> null
                }
            } catch (exception: IOException) {
                Log.w(TAG, "Could not open the saved wallpaper URI: $selectedUri", exception)
                null
            }
            val wallpaper = selectedWallpaper ?: WallpaperManager.getInstance(view.context).drawable
                ?: return true
            view.background = CenterCropDrawable(copyDrawable(wallpaper, view))
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Permission is required to read the saved or device wallpaper", exception)
            false
        }
    }

    fun saveResource(context: Context, resourceId: Int) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(RESOURCE_KEY, resourceId)
            .remove(URI_KEY)
            .apply()
    }

    fun saveUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(URI_KEY, uri.toString())
            .remove(RESOURCE_KEY)
            .apply()
    }

    fun getSavedResource(context: Context): Int? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return if (preferences.contains(RESOURCE_KEY)) preferences.getInt(RESOURCE_KEY, 0) else null
    }

    fun getSavedUri(context: Context): Uri? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(URI_KEY, null)
            ?.let(Uri::parse)

    private fun copyDrawable(drawable: Drawable, view: View): Drawable =
        drawable.constantState
            ?.newDrawable(view.resources)
            ?.mutate()
            ?: drawable.mutate()

    private const val PREFERENCES_NAME = "launcher_wallpaper_preferences"
    private const val RESOURCE_KEY = "wallpaper_resource"
    private const val URI_KEY = "wallpaper_uri"
    private const val TAG = "LauncherWallpaper"
}
