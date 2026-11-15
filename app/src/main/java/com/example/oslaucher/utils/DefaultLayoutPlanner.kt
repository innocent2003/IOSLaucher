package com.example.oslaucher.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.example.oslaucher.models.AppInfo

/**
 * Builds the first-run home layout: which apps go in the dock, which Google apps are grouped
 * into a "Google" folder, and in what order the remaining icons fill the first page.
 *
 * Apps are matched by the role they play on the device (default dialer, browser, camera...)
 * with known package names as a fallback, so the layout works across phone brands.
 */
internal object DefaultLayoutPlanner {
    const val GOOGLE_FOLDER_ID = "default_google_folder"

    data class Plan(
        /** Dock package for each slot 0..3 (missing roles are left out). */
        val dockSlots: Map<String, Int>,
        /** Packages to group into the Google folder (empty when fewer than 2 are installed). */
        val googleFolderPackages: List<String>,
        /** Leading grid order; entries may be [folderKey]. Remaining apps follow afterwards. */
        val gridOrder: List<String>
    )

    fun plan(
        context: Context,
        apps: List<AppInfo>,
        launcherPackage: String,
        folderKey: String
    ): Plan {
        val installed = apps.mapTo(linkedSetOf()) { it.packageName }
        val used = mutableSetOf<String>()
        val packageManager = context.packageManager

        fun firstInstalled(vararg candidates: String?): String? =
            candidates.firstOrNull { it != null && it in installed && it !in used }

        fun byKeyword(vararg keywords: String): String? = apps.firstOrNull { app ->
            app.packageName !in used &&
                keywords.any { app.packageName.contains(it, ignoreCase = true) }
        }?.packageName

        fun claim(packageName: String?): String? = packageName?.also { used += it }

        // ---- Dock: phone, messages, browser, app store ----
        val dock = listOf(
            claim(
                firstInstalled(
                    resolve(packageManager, Intent(Intent.ACTION_DIAL)),
                    "com.google.android.dialer",
                    "com.android.contacts",
                    "com.samsung.android.dialer"
                )
            ),
            claim(
                firstInstalled(
                    resolve(packageManager, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
                    resolveCategory(packageManager, Intent.CATEGORY_APP_MESSAGING),
                    "com.google.android.apps.messaging",
                    "com.android.mms",
                    "com.samsung.android.messaging"
                )
            ),
            claim(
                firstInstalled(
                    "com.android.chrome",
                    resolveCategory(packageManager, Intent.CATEGORY_APP_BROWSER)
                )
            ),
            claim(firstInstalled("com.android.vending"))
        )
        val dockSlots = buildMap {
            dock.forEachIndexed { slot, packageName -> if (packageName != null) put(packageName, slot) }
        }

        // ---- Google folder ----
        val googleFolder = GOOGLE_FOLDER_APPS.filter { it in installed && it !in used }
            .takeIf { it.size >= 2 }
            .orEmpty()
        used += googleFolder

        // ---- First page order, following the reference layout ----
        val grid = mutableListOf<String>()
        fun add(packageName: String?) {
            if (packageName != null && packageName in installed && grid.add(packageName)) {
                used += packageName
            }
        }
        add(launcherPackage.takeIf { it in installed })
        add(byKeyword("weather"))
        add(
            firstInstalled(
                resolveCategory(packageManager, Intent.CATEGORY_APP_CALENDAR),
                "com.google.android.calendar"
            ) ?: byKeyword("calendar")
        )
        add(
            firstInstalled(
                resolveCategory(packageManager, Intent.CATEGORY_APP_CONTACTS),
                "com.android.contacts",
                "com.google.android.contacts"
            )
        )
        add(firstInstalled(resolve(packageManager, Intent(Settings.ACTION_SETTINGS)), "com.android.settings"))
        if (googleFolder.isNotEmpty()) grid += folderKey
        add(
            firstInstalled(
                resolveCategory(packageManager, Intent.CATEGORY_APP_GALLERY),
                "com.coloros.gallery3d",
                "com.miui.gallery",
                "com.sec.android.gallery3d"
            ) ?: byKeyword("gallery")
        )
        add(byKeyword("phonemanager", "securitycenter", "safecenter", "devicecare"))
        add(
            firstInstalled(resolveCategory(packageManager, Intent.CATEGORY_APP_BROWSER))
                ?: byKeyword("browser")
        )
        add(
            firstInstalled(resolve(packageManager, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
                ?: byKeyword("camera")
        )
        add(byKeyword("gamespace", "games.assistant", "gamecenter", "game.assistant"))
        add(firstInstalled("com.heytap.market", "com.oppo.market", "com.xiaomi.market", "com.sec.android.app.samsungapps"))
        add(byKeyword("myoppo", "heytap.vip", "oppo.vip"))

        return Plan(dockSlots, googleFolder, grid)
    }

    private fun resolve(packageManager: PackageManager, intent: Intent): String? =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeUnless { it == "android" }

    private fun resolveCategory(packageManager: PackageManager, category: String): String? =
        resolve(packageManager, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category))

    private val GOOGLE_FOLDER_APPS = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.gm",
        "com.google.android.youtube",
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.google.android.apps.maps",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.youtube.music",
        "com.google.android.keep",
        "com.google.android.apps.subscriptions.red",
        "com.google.android.videos",
        "com.google.android.apps.magazines"
    )
}
