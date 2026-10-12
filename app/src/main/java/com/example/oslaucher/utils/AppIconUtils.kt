package com.example.oslaucher.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.example.oslaucher.models.AppInfo
import java.io.IOException

object AppIconUtils {
    private const val PREFERENCES_NAME = "launcher_settings"
    private const val ICON_KEY_PREFIX = "custom_app_icon_"

    fun getIcon(context: Context, app: AppInfo): Drawable {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val iconUri = preferences.getString(iconKey(app.packageName), null) ?: return app.icon

        return try {
            val uri = Uri.parse(iconUri)
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No stream returned for custom icon")
            stream.use { input ->
                Drawable.createFromStream(input, null)
                    ?: throw IOException("Unable to decode custom icon")
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to load custom icon for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
            app.icon
        } catch (exception: SecurityException) {
            Log.w(TAG, "No permission to load custom icon for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
            app.icon
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Invalid custom icon URI for ${app.packageName}", exception)
            preferences.edit().remove(iconKey(app.packageName)).apply()
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
    }

    fun clearCustomIcon(context: Context, packageName: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(iconKey(packageName))
            .apply()
    }

    private fun iconKey(packageName: String) = ICON_KEY_PREFIX + packageName

    private const val TAG = "AppIconUtils"
}
