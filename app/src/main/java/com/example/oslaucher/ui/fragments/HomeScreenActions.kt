package com.example.oslaucher.ui.fragments

import com.example.oslaucher.models.AppInfo

interface HomeScreenActions {
    fun openApps()
    fun openSettings()
    fun loadHomeScreenApps(onLoaded: (List<AppInfo>) -> Unit)
    fun launchHomeScreenApp(packageName: String)
    fun openHomeScreenFolder(name: String, apps: List<AppInfo>)
}
