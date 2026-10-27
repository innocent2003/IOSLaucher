package com.example.oslaucher.repositories

import android.content.Context
import android.util.Log
import com.example.oslaucher.models.AppFolder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class LauncherLayoutRepository(context: Context) {
    private val folderPreferences = context.getSharedPreferences(
        APP_FOLDER_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val appGridPreferences = context.getSharedPreferences(
        APP_GRID_ORDER_PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun loadAppGridOrder(): List<String?> {
        val stored = appGridPreferences.getString(APP_GRID_ORDER_KEY, null) ?: return emptyList()
        return try {
            val packages = JSONArray(stored)
            buildList {
                for (index in 0 until packages.length()) {
                    add(if (packages.isNull(index)) null else packages.optString(index))
                }
            }
        } catch (exception: JSONException) {
            Log.w(TAG, "Could not read saved app grid order", exception)
            emptyList()
        }
    }

    fun saveAppGridOrder(packages: List<String?>) {
        appGridPreferences.edit()
            .putString(APP_GRID_ORDER_KEY, JSONArray(packages).toString())
            .apply()
    }

    fun loadAppFolders(): List<AppFolder> {
        val stored = folderPreferences.getString(APP_FOLDERS_KEY, null) ?: return emptyList()
        return try {
            val jsonFolders = JSONArray(stored)
            buildList {
                for (index in 0 until jsonFolders.length()) {
                    val jsonFolder = jsonFolders.getJSONObject(index)
                    val jsonPackages = jsonFolder.getJSONArray(PACKAGES_KEY)
                    val packageNames = buildList {
                        for (packageIndex in 0 until jsonPackages.length()) {
                            add(jsonPackages.getString(packageIndex))
                        }
                    }
                    if (packageNames.size >= 2) {
                        add(
                            AppFolder(
                                jsonFolder.getString(ID_KEY),
                                jsonFolder.getString(NAME_KEY),
                                packageNames
                            )
                        )
                    }
                }
            }
        } catch (exception: JSONException) {
            Log.w(TAG, "Could not read saved app folders", exception)
            emptyList()
        }
    }

    fun saveAppFolders(folders: List<AppFolder>) {
        val jsonFolders = JSONArray()
        folders.forEach { folder ->
            jsonFolders.put(
                JSONObject()
                    .put(ID_KEY, folder.id)
                    .put(NAME_KEY, folder.name)
                    .put(PACKAGES_KEY, JSONArray(folder.packageNames))
            )
        }
        folderPreferences.edit()
            .putString(APP_FOLDERS_KEY, jsonFolders.toString())
            .apply()
    }

    fun loadDockFolderSlots(folders: List<AppFolder>): Map<String, Int> {
        val stored = folderPreferences.getString(DOCK_FOLDER_SLOTS_KEY, null)
            ?: return emptyMap()
        return try {
            val jsonSlots = JSONObject(stored)
            buildMap {
                val folderIds = folders.mapTo(mutableSetOf()) { it.id }
                val ids = jsonSlots.keys()
                while (ids.hasNext()) {
                    val id = ids.next()
                    if (id in folderIds) put(id, jsonSlots.getInt(id).coerceIn(0, 3))
                }
            }
        } catch (exception: JSONException) {
            Log.w(TAG, "Could not read dock folder positions", exception)
            emptyMap()
        }
    }

    fun saveDockFolderSlots(slots: Map<String, Int>) {
        val jsonSlots = JSONObject()
        slots.forEach { (id, slot) -> jsonSlots.put(id, slot) }
        folderPreferences.edit()
            .putString(DOCK_FOLDER_SLOTS_KEY, jsonSlots.toString())
            .apply()
    }

    fun loadDockAppSlots(): Map<String, Int> {
        val stored = folderPreferences.getString(DOCK_APP_SLOTS_KEY, null) ?: return emptyMap()
        return try {
            val jsonSlots = JSONObject(stored)
            buildMap {
                val packages = jsonSlots.keys()
                while (packages.hasNext()) {
                    val packageName = packages.next()
                    put(packageName, jsonSlots.getInt(packageName).coerceIn(0, 3))
                }
            }
        } catch (exception: JSONException) {
            Log.w(TAG, "Could not read dock app positions", exception)
            emptyMap()
        }
    }

    fun saveDockAppSlots(slots: Map<String, Int>) {
        val jsonSlots = JSONObject()
        slots.forEach { (packageName, slot) -> jsonSlots.put(packageName, slot) }
        folderPreferences.edit()
            .putString(DOCK_APP_SLOTS_KEY, jsonSlots.toString())
            .apply()
    }

    fun loadDockEmptySlots(): Set<Int> =
        folderPreferences.getStringSet(DOCK_EMPTY_SLOTS_KEY, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filterTo(mutableSetOf()) { it in 0..3 }

    fun saveDockEmptySlots(slots: Set<Int>) {
        folderPreferences.edit()
            .putStringSet(DOCK_EMPTY_SLOTS_KEY, slots.map(Int::toString).toSet())
            .apply()
    }

    fun loadDockRemovedAppPackages(): Set<String> =
        folderPreferences.getStringSet(DOCK_REMOVED_APP_PACKAGES_KEY, emptySet()).orEmpty()

    fun saveDockRemovedAppPackages(packageNames: Set<String>) {
        folderPreferences.edit()
            .putStringSet(DOCK_REMOVED_APP_PACKAGES_KEY, packageNames.toSet())
            .apply()
    }

    private companion object {
        const val APP_FOLDER_PREFERENCES = "launcher_app_folders"
        const val APP_FOLDERS_KEY = "folders"
        const val DOCK_FOLDER_SLOTS_KEY = "dock_folder_slots"
        const val DOCK_APP_SLOTS_KEY = "dock_app_slots"
        const val DOCK_EMPTY_SLOTS_KEY = "dock_empty_slots"
        const val DOCK_REMOVED_APP_PACKAGES_KEY = "dock_removed_app_packages"
        const val APP_GRID_ORDER_PREFERENCES = "launcher_app_grid_order"
        const val APP_GRID_ORDER_KEY = "packages"
        const val ID_KEY = "id"
        const val NAME_KEY = "name"
        const val PACKAGES_KEY = "packages"
        const val TAG = "LauncherLayoutRepository"
    }
}
