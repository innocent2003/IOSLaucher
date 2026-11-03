package com.example.oslaucher.utils

import android.content.Context

internal object HiddenAppsPreferences {
    private const val PREFERENCES_NAME = "hidden_apps"
    private const val HIDDEN_PACKAGES_KEY = "hidden_packages"

    fun packages(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getStringSet(HIDDEN_PACKAGES_KEY, emptySet())
            .orEmpty()
            .toSet()

    fun save(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(HIDDEN_PACKAGES_KEY, packages.toSet())
            .apply()
    }

    fun hide(context: Context, packageName: String) {
        save(context, packages(context) + packageName)
    }

    fun unhide(context: Context, packageName: String) {
        save(context, packages(context) - packageName)
    }
}
