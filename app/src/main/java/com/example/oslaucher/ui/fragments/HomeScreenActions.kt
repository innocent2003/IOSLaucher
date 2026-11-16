package com.example.oslaucher.ui.fragments

import android.view.View
import com.example.oslaucher.models.AppInfo

interface HomeScreenActions {
    fun openApps()
    fun openSettings()
    fun loadHomeScreenApps(onLoaded: (List<AppInfo>) -> Unit)
    fun launchHomeScreenApp(packageName: String)
    fun openHomeScreenFolder(name: String, apps: List<AppInfo>)

    /** A previously built (now detached) view for [layoutResId], kept to avoid rebuilding it. */
    fun cachedHomeScreenView(layoutResId: Int): View?
    fun storeHomeScreenView(layoutResId: Int, view: View)
}
