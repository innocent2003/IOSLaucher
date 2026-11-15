package com.example.oslaucher.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import com.example.oslaucher.models.AppInfo
import java.io.IOException

object AppIconUtils {
    private const val PREFERENCES_NAME = "launcher_settings"
    private const val ICON_KEY_PREFIX = "custom_app_icon_"

    // In-memory cache for custom icons so we don't re-read from disk on every grid render.
    // Keyed by URI string; size limit of 32 entries covers all visible dock/grid icons.
    private val iconCache = object : LruCache<String, Drawable>(32) {}

    fun getIcon(context: Context, app: AppInfo): Drawable {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val iconUri = preferences.getString(iconKey(app.packageName), null) ?: return app.icon

        // Return cached drawable if available
        iconCache.get(iconUri)?.let { return it }

        return try {
            val uri = Uri.parse(iconUri)
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No stream returned for custom icon")
            stream.use { input ->
                Drawable.createFromStream(input, null)
                    ?: throw IOException("Unable to decode custom icon")
            }.also { drawable ->
                // Cache successful loads to avoid repeated disk I/O on every redraw
                iconCache.put(iconUri, drawable)
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to load custom icon for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
            iconCache.remove(iconUri)
            app.icon
        } catch (exception: SecurityException) {
            Log.w(TAG, "No permission to load custom icon for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
            iconCache.remove(iconUri)
            app.icon
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Invalid custom icon URI for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
            iconCache.remove(iconUri)
            app.icon
        }
    }

    fun hasCustomIcon(context: Context, packageName: String): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .contains(iconKey(packageName))

    fun setCustomIcon(context: Context, packageName: String, uri: Uri) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(iconKey(packageName), uri.toString())
            .apply()
        // Evict old cache entry for this package so the new icon is loaded on next render
        val oldUri = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(iconKey(packageName), null)
        if (oldUri != null) iconCache.remove(oldUri)
    }

    fun clearCustomIcon(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val oldUri = prefs.getString(iconKey(packageName), null)
        prefs.edit().remove(iconKey(packageName)).apply()
        if (oldUri != null) iconCache.remove(oldUri)
    }

    private fun iconKey(packageName: String) = ICON_KEY_PREFIX + packageName

    private const val TAG = "AppIconUtils"
}
