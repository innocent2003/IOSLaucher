package com.example.oslaucher.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.oslaucher.models.AppInfo
import java.text.Collator
import java.util.Locale


object InstalledAppUtils {
    fun queryLaunchableApps(packageManager: PackageManager): List<AppInfo> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())

        // Use the non-deprecated ResolveInfoFlags API on Android 13+ to avoid log warnings
        @Suppress("DEPRECATION")
        val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launchIntent, 0)
        }

        // Map each launchable app to its install timestamp and sort by most recent install first
        val appsWithInstallTime = resolvedActivities
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .mapNotNull { appInfo ->
                try {
                    val pkgInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                    val installTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // firstInstallTime is available from API 9, but use safer method for newer APIs
                        pkgInfo.firstInstallTime
                    } else {
                        pkgInfo.firstInstallTime
                    }
                    Triple(appInfo, installTime, packageManager.getApplicationLabel(appInfo).toString())
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedWith(Comparator { a, b ->
                // Sort by install time ascending (oldest first); if equal, sort alphabetically
                val timeDiff = a.second.compareTo(b.second) // older first
                if (timeDiff != 0) timeDiff else collator.compare(a.third, b.third)
            })

        return appsWithInstallTime.map { (appInfo, _, _) ->
            AppInfo(
                packageName = appInfo.packageName,
                name = packageManager.getApplicationLabel(appInfo).toString(),
                icon = packageManager.getApplicationIcon(appInfo)
            )
        }
    }

    fun loadCachedApp(packageManager: PackageManager, packageName: String, name: String): AppInfo? {
        return try {
            AppInfo(packageName, name, packageManager.getApplicationIcon(packageName))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
